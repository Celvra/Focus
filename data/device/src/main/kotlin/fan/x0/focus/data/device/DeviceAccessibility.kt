package fan.x0.focus.data.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import fan.x0.focus.data.device.system.DeviceAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.coroutines.resume

data class DeviceWindowShot(
    val image: String,
    val bounds: Rect,
)

@Single
class DeviceAccessibility(@Provided private val context: Context) {
    private val shotMutex = Mutex()
    private var lastShotAt = 0L

    fun ready(): Boolean = DeviceAccessibilityService.current != null

    fun service(): AccessibilityService? = DeviceAccessibilityService.current

    suspend fun snapshot(
        deviceId: String = "local",
        deviceName: String = Build.MODEL.orEmpty(),
        maxNodes: Int = 180,
    ): DeviceSnapshot = withContext(Dispatchers.Main) {
        val service = DeviceAccessibilityService.current
            ?: return@withContext DeviceSnapshot(
                deviceId = deviceId,
                deviceName = deviceName,
                text = "Accessibility service is not enabled",
            )
        val metrics = context.resources.displayMetrics
        val windows = runCatching { service.windows.toList() }.getOrDefault(emptyList())
        val active = targetWindow(windows, context.packageName)
        val root = active?.root
            ?: service.rootInActiveWindow?.takeIf {
                it.packageName?.toString() != context.packageName
            }
        val keyguard = runCatching {
            context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        }.getOrDefault(false)
        val screenOn = runCatching {
            context.getSystemService(PowerManager::class.java)?.isInteractive != false
        }.getOrDefault(true)
        val imePackages = imePackages()
        val keyboard = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        val nodes = mutableListOf<DeviceNode>()
        val seen = mutableSetOf<String>()
        if (root != null) {
            collect(
                node = root,
                depth = 0,
                maxNodes = maxNodes,
                imePackages = imePackages,
                nodes = nodes,
                seen = seen,
            )
        }
        val packageName = root?.packageName?.toString().orEmpty()
        val title = active?.title?.toString().orEmpty()
        val snapshot = DeviceSnapshot(
            deviceId = deviceId,
            deviceName = deviceName,
            packageName = packageName,
            windowTitle = title,
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            keyguard = keyguard,
            screenOn = screenOn,
            keyboard = keyboard,
            nodes = nodes,
        )
        snapshot.copy(text = render(snapshot))
    }

    suspend fun screenshot(): String? = shotMutex.withLock {
        val service = DeviceAccessibilityService.current ?: return@withLock null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withLock null
        val wait = 350L - (System.currentTimeMillis() - lastShotAt)
        if (wait > 0) delay(wait)
        lastShotAt = System.currentTimeMillis()
        val bitmap = capture(service)
        if (bitmap == null) return@withLock null
        val encoded = withContext(Dispatchers.IO) { DeviceImage.encode(bitmap) }
        bitmap.recycle()
        encoded
    }

    suspend fun screenshotWindow(): DeviceWindowShot? = shotMutex.withLock {
        val service = DeviceAccessibilityService.current ?: return@withLock null
        if (Build.VERSION.SDK_INT < 34) return@withLock null
        val windows = runCatching { service.windows.toList() }.getOrDefault(emptyList())
        val window = windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive
        } ?: windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION
        } ?: return@withLock null
        val bounds = Rect().also { window.getBoundsInScreen(it) }
        if (bounds.width() <= 0 || bounds.height() <= 0) return@withLock null
        val wait = 350L - (System.currentTimeMillis() - lastShotAt)
        if (wait > 0) delay(wait)
        lastShotAt = System.currentTimeMillis()
        val bitmap = captureWindow(service, window.id) ?: return@withLock null
        val encoded = withContext(Dispatchers.IO) { DeviceImage.encode(bitmap) }
        bitmap.recycle()
        DeviceWindowShot(encoded, bounds)
    }

    suspend fun screenshotBytes(): ByteArray? {
        val encoded = screenshot() ?: return null
        return runCatching { android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP) }.getOrNull()
    }

    suspend fun tap(bounds: DeviceBounds, longPress: Boolean = false): Boolean {
        if (!bounds.valid) return false
        return gestureTap(bounds.centerX.toFloat(), bounds.centerY.toFloat(), if (longPress) 700 else 60)
    }

    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
    ): Boolean = withContext(Dispatchers.Main) {
        val service = DeviceAccessibilityService.current ?: return@withContext false
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        dispatch(service, path, durationMs)
    }

    suspend fun type(
        text: String,
        bounds: DeviceBounds? = null,
        clear: Boolean = true,
        submit: Boolean = false,
    ): Boolean = withContext(Dispatchers.Main) {
        val service = DeviceAccessibilityService.current ?: return@withContext false
        if (bounds != null) {
            gestureTapOnMain(service, bounds.centerX.toFloat(), bounds.centerY.toFloat(), 60)
            delay(220)
        }
        val focused = focusedInput(service)
        var done = false
        if (focused != null) {
            val value = if (clear) text else (focused.text?.toString().orEmpty() + text)
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            done = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (done && submit) {
                runCatching {
                    focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                }
            }
        }
        if (!done && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val connection = service.inputMethod?.currentInputConnection
            if (connection != null) {
                val length = focused?.text?.length ?: 0
                if (clear && length > 0) {
                    runCatching { connection.setSelection(0, length) }
                }
                runCatching { connection.commitText(text, 1, null) }
                if (submit) {
                    runCatching {
                        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    }
                }
                done = true
            }
        }
        done
    }

    suspend fun key(name: String): Boolean = withContext(Dispatchers.Main) {
        val service = DeviceAccessibilityService.current ?: return@withContext false
        when (name) {
            "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            "enter" -> sendKey(service, KeyEvent.KEYCODE_ENTER)
            "delete" -> sendKey(service, KeyEvent.KEYCODE_DEL)
            "escape" -> sendKey(service, KeyEvent.KEYCODE_ESCAPE)
            "wake" -> wake()
            else -> false
        }
    }

    suspend fun openApp(packageName: String, activity: String = ""): Boolean =
        withContext(Dispatchers.Main) {
            val manager = context.packageManager
            val intents = buildList {
                if (activity.isNotBlank()) {
                    add(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(ComponentName(packageName, activity)),
                    )
                }
                manager.getLaunchIntentForPackage(packageName)?.let { add(it) }
                manager.getLeanbackLaunchIntentForPackage(packageName)?.let { add(it) }
                queryLauncher(packageName).firstOrNull()?.let { info ->
                    add(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(ComponentName(packageName, info.activityInfo.name)),
                    )
                }
            }
            for (intent in intents.distinctBy { it.component ?: it.action }) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                if (runCatching { context.startActivity(intent) }.isSuccess) return@withContext true
            }
            false
        }

    suspend fun currentPackage(): String? = withContext(Dispatchers.Main) {
        val service = DeviceAccessibilityService.current ?: return@withContext null
        val windows = runCatching { service.windows.toList() }.getOrDefault(emptyList())
        targetWindow(windows, context.packageName)?.root?.packageName?.toString()
    }

    suspend fun resolveApp(query: String): DeviceApp? = withContext(Dispatchers.IO) {
        val value = query.trim()
        if (value.isBlank()) return@withContext null
        val launchable = launchableApps()
        launchable.firstOrNull {
            it.packageName.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true)
        }?.let { return@withContext it }
        launchable.filter {
            it.label.startsWith(value, ignoreCase = true) || it.packageName.startsWith(value, ignoreCase = true)
        }.minByOrNull { it.label.length }?.let { return@withContext it }
        launchable.filter {
            it.label.contains(value, ignoreCase = true) || it.packageName.contains(value, ignoreCase = true)
        }.minByOrNull { it.label.length }?.let { return@withContext it }
        val manager = context.packageManager
        if (value.contains('.')) {
            val info = runCatching { manager.getApplicationInfo(value, 0) }.getOrNull()
            if (info != null) {
                return@withContext DeviceApp(
                    label = runCatching { manager.getApplicationLabel(info).toString() }
                        .getOrDefault(value),
                    packageName = value,
                    activity = launchActivity(value),
                )
            }
        }
        val installed = runCatching { manager.getInstalledApplications(0) }.getOrDefault(emptyList())
        installed.firstOrNull {
            runCatching { manager.getApplicationLabel(it).toString() }.getOrDefault("")
                .equals(value, ignoreCase = true)
        }?.let { info ->
            return@withContext DeviceApp(
                label = runCatching { manager.getApplicationLabel(info).toString() }
                    .getOrDefault(value),
                packageName = info.packageName,
                activity = launchActivity(info.packageName),
            )
        }
        null
    }

    suspend fun apps(query: String = "", limit: Int = 60): List<DeviceApp> = withContext(Dispatchers.IO) {
        val value = query.trim()
        launchableApps()
            .filter { value.isBlank() || it.label.contains(value, ignoreCase = true) || it.packageName.contains(value, ignoreCase = true) }
            .take(limit.coerceIn(1, 300))
    }

    private fun launchableApps(): List<DeviceApp> {
        val manager = context.packageManager
        return queryLauncher().asSequence()
            .map { info ->
                DeviceApp(
                    label = info.loadLabel(manager).toString(),
                    packageName = info.activityInfo.packageName,
                    activity = info.activityInfo.name,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun launchActivity(packageName: String): String =
        queryLauncher(packageName).firstOrNull()?.activityInfo?.name.orEmpty()

    private fun queryLauncher(packageName: String? = null): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        if (!packageName.isNullOrBlank()) intent.setPackage(packageName)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, 0)
            }
        }.getOrDefault(emptyList())
    }

    fun clipboard(): String = runCatching {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return ""
        manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }.getOrDefault("")

    fun setClipboard(text: String): Boolean = runCatching {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
        manager.setPrimaryClip(ClipData.newPlainText("Focus", text))
        true
    }.getOrDefault(false)

    private suspend fun gestureTap(x: Float, y: Float, durationMs: Long): Boolean =
        withContext(Dispatchers.Main) {
            val service = DeviceAccessibilityService.current ?: return@withContext false
            gestureTapOnMain(service, x, y, durationMs)
        }

    private suspend fun gestureTapOnMain(
        service: AccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long,
    ): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(service, path, durationMs)
    }

    private suspend fun dispatch(
        service: AccessibilityService,
        path: Path,
        durationMs: Long,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1)))
            .build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
        val accepted = service.dispatchGesture(gesture, callback, null)
        if (!accepted && continuation.isActive) continuation.resume(false)
    }

    private fun targetWindow(
        windows: List<AccessibilityWindowInfo>,
        ownPackage: String,
    ): AccessibilityWindowInfo? {
        val candidates = windows.filter { window ->
            window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                runCatching { window.root?.packageName?.toString() }.getOrNull() != ownPackage
        }
        return candidates.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive
        } ?: candidates.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
        } ?: candidates.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION
        } ?: candidates.firstOrNull { it.isActive } ?: candidates.firstOrNull()
    }

    private fun focusedInput(service: AccessibilityService): AccessibilityNodeInfo? {
        val windows = runCatching { service.windows.toList() }.getOrDefault(emptyList())
        val target = targetWindow(windows, context.packageName)
        return target?.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?.takeIf { it.packageName?.toString() != context.packageName }
    }

    private fun sendKey(service: AccessibilityService, keyCode: Int): Boolean {
        val focused = focusedInput(service)
        if (keyCode == KeyEvent.KEYCODE_ENTER && focused != null) {
            val done = runCatching {
                focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }.getOrDefault(false)
            if (done) return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val connection = service.inputMethod?.currentInputConnection
            if (connection != null) {
                return runCatching {
                    connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                    true
                }.getOrDefault(false)
            }
        }
        if (focused == null || keyCode != KeyEvent.KEYCODE_ENTER) return false
        return runCatching {
            focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }.getOrDefault(false)
    }

    private fun wake(): Boolean = runCatching {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val lock = manager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "focus:device:wake",
        )
        lock.acquire(5_000)
        true
    }.getOrDefault(false)

    private suspend fun captureWindow(service: AccessibilityService, windowId: Int): Bitmap? {
        var bitmap: Bitmap? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        withContext(Dispatchers.Main) {
            service.takeScreenshotOfWindow(
                windowId,
                ContextCompat.getMainExecutor(context),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        latch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        latch.countDown()
                    }
                },
            )
        }
        withContext(Dispatchers.IO) {
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        }
        return bitmap
    }

    private suspend fun capture(service: AccessibilityService): Bitmap? {
        var bitmap: Bitmap? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        withContext(Dispatchers.Main) {
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(context),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        latch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        latch.countDown()
                    }
                },
            )
        }
        withContext(Dispatchers.IO) {
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        }
        return bitmap
    }

    private fun imePackages(): Set<String> = runCatching {
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return emptySet()
        manager.enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    private fun collect(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxNodes: Int,
        imePackages: Set<String>,
        nodes: MutableList<DeviceNode>,
        seen: MutableSet<String>,
    ) {
        if (nodes.size >= maxNodes) return
        val packageName = node.packageName?.toString().orEmpty()
        if (packageName in imePackages && depth > 0) return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val deviceBounds = DeviceBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (deviceBounds.valid && (node.isVisibleToUser || node.isClickable || node.isEditable)) {
            val password = node.isPassword
            val text = if (password) "" else node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            val hint = node.hintText?.toString()?.trim().orEmpty()
            val className = node.className?.toString()?.substringAfterLast('.').orEmpty()
            val viewId = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
            val clickable = node.isClickable
            val editable = node.isEditable
            val scrollable = node.isScrollable
            val focused = node.isFocused
            val checked = node.isChecked
            val selected = node.isSelected
            val label = text.ifBlank { description }.ifBlank { hint }
            val keep = label.isNotBlank() || clickable || editable || scrollable || password || checked || selected
            if (keep) {
                val signature = "${deviceBounds.left},${deviceBounds.top},${deviceBounds.right},${deviceBounds.bottom}|$label|$className"
                if (seen.add(signature)) {
                    nodes += DeviceNode(
                        index = nodes.size,
                        text = text,
                        description = description,
                        hint = hint,
                        className = className,
                        packageName = packageName,
                        viewId = viewId,
                        bounds = deviceBounds,
                        clickable = clickable,
                        editable = editable,
                        scrollable = scrollable,
                        password = password,
                        focused = focused,
                        checked = checked,
                        selected = selected,
                    )
                }
            }
        }
        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            collect(child, depth + 1, maxNodes, imePackages, nodes, seen)
            if (nodes.size >= maxNodes) return
        }
    }

    private fun render(snapshot: DeviceSnapshot): String {
        val builder = StringBuilder()
        builder.append("device: ").append(snapshot.deviceName.ifBlank { snapshot.deviceId }).append('\n')
        builder.append("app: ").append(snapshot.packageName.ifBlank { "-" })
        builder.append("  title: ").append(snapshot.windowTitle.ifBlank { "-" })
        builder.append("  screen: ").append(snapshot.width).append('x').append(snapshot.height).append('\n')
        builder.append("keyguard: ").append(snapshot.keyguard)
        builder.append("  screen_on: ").append(snapshot.screenOn)
        builder.append("  keyboard: ").append(snapshot.keyboard).append('\n')
        if (snapshot.nodes.isEmpty()) {
            builder.append("(no accessible nodes)")
            return builder.toString()
        }
        snapshot.nodes.forEach { node ->
            builder.append('[').append(node.index).append(']')
            builder.append(' ').append(node.label.ifBlank { node.className })
            builder.append(" (").append(node.className.ifBlank { "View" }).append(")")
            if (node.viewId.isNotBlank()) builder.append(" id=").append(node.viewId)
            builder.append(" bounds=")
                .append(node.bounds.left).append(',')
                .append(node.bounds.top).append(',')
                .append(node.bounds.right).append(',')
                .append(node.bounds.bottom)
            if (node.clickable) builder.append(" clickable")
            if (node.editable) builder.append(" editable")
            if (node.scrollable) builder.append(" scrollable")
            if (node.password) builder.append(" password")
            if (node.focused) builder.append(" focused")
            if (node.checked) builder.append(" checked")
            if (node.selected) builder.append(" selected")
            builder.append('\n')
        }
        return builder.toString().trimEnd()
    }
}
