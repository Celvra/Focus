package fan.x0.focus.data.device.display

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

internal class DisplaySession(private val context: Context) {
    private val lock = java.lang.Object()
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var frameThread: HandlerThread? = null
    private var frame: Bitmap? = null
    private var frameWidth = 0
    private var frameHeight = 0
    @Volatile
    private var frameReady = false

    @Volatile
    private var displayId = 0

    @Volatile
    private var width = 0

    @Volatile
    private var height = 0

    @Volatile
    private var density = 0

    @Volatile
    private var trusted = false
    private var displayWake: PowerManager.WakeLock? = null
    private var cpuWake: PowerManager.WakeLock? = null

    fun start(requestedWidth: Int, requestedHeight: Int, requestedDensity: Int): DisplayStartResult {
        synchronized(lock) {
            if (display != null && displayId > 0) return currentResult()
            stopLocked()
            require(requestedWidth in 320..2560) { "width out of range" }
            require(requestedHeight in 320..2560) { "height out of range" }
            require(requestedDensity in 120..640) { "density out of range" }
            width = requestedWidth
            height = requestedHeight
            density = requestedDensity
            frameThread = HandlerThread("focus-display-frames").also { it.start() }
            val createdReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            createdReader.setOnImageAvailableListener(
                { source -> onFrame(source) },
                Handler(frameThread?.looper ?: android.os.Looper.getMainLooper()),
            )
            reader = createdReader
            val base = baseFlags()
            val extra = trustedFlags()
            var useTrusted = extra != 0
            val created = try {
                createDisplay(width, height, density, base or extra)
            } catch (first: Throwable) {
                if (!useTrusted) throw first
                val withoutUnlock = extra and flag("ALWAYS_UNLOCKED").inv()
                try {
                    createDisplay(width, height, density, base or withoutUnlock)
                } catch (second: Throwable) {
                    useTrusted = false
                    createDisplay(width, height, density, base)
                }
            }
            val id = created.display?.displayId ?: -1
            if (id <= 0) {
                stopLocked()
                throw IllegalStateException("virtual display returned invalid id")
            }
            display = created
            displayId = id
            trusted = useTrusted
            applyImePolicy(id)
            acquireWakeLocks(id)
            return currentResult()
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    fun status(): DisplayStatus = synchronized(lock) {
        DisplayStatus(
            running = display != null && displayId > 0,
            displayId = if (display != null) displayId else 0,
            width = width,
            height = height,
            density = density,
            trusted = trusted,
            frameReady = frameReady,
        )
    }

    fun capture(quality: Int, maxWidth: Int): ByteArray? {
        val source = synchronized(lock) {
            if (reader == null) return null
            if (!frameReady) runCatching { lock.wait(3_000) }
            val bitmap = frame ?: return null
            val croppedWidth = frameWidth.coerceAtMost(bitmap.width)
            val croppedHeight = frameHeight.coerceAtMost(bitmap.height)
            if (croppedWidth <= 0 || croppedHeight <= 0) return null
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, croppedWidth, croppedHeight)
            val copy = cropped.copy(Bitmap.Config.ARGB_8888, false)
            if (cropped !== bitmap) cropped.recycle()
            copy
        }
        var output = source
        try {
            if (maxWidth in 1 until output.width) {
                val scaledHeight = (output.height.toLong() * maxWidth / output.width).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(output, maxWidth, scaledHeight, true)
                if (scaled !== output) output.recycle()
                output = scaled
            }
            val bytes = ByteArrayOutputStream()
            output.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), bytes)
            return bytes.toByteArray()
        } finally {
            if (!output.isRecycled) output.recycle()
        }
    }

    fun tap(requestedDisplayId: Int, x: Int, y: Int, durationMs: Int): Boolean {
        if (!valid(requestedDisplayId, x, y)) return false
        return runCatching {
            touch(x, y, x, y, durationMs)
            true
        }.getOrDefault(false)
    }

    fun swipe(
        requestedDisplayId: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int,
    ): Boolean {
        if (!valid(requestedDisplayId, startX, startY)) return false
        if (!valid(requestedDisplayId, endX, endY)) return false
        return runCatching {
            touch(startX, startY, endX, endY, durationMs)
            true
        }.getOrDefault(false)
    }

    fun key(requestedDisplayId: Int, keyCode: Int): Boolean {
        if (requestedDisplayId != displayId) return false
        if (keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
            keyCode == KeyEvent.KEYCODE_POWER ||
            keyCode == KeyEvent.KEYCODE_SLEEP ||
            keyCode == KeyEvent.KEYCODE_WAKEUP
        ) {
            return false
        }
        return runCatching {
            val now = SystemClock.uptimeMillis()
            inject(keyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode))
            inject(keyEvent(now, now, KeyEvent.ACTION_UP, keyCode))
            true
        }.getOrDefault(false)
    }

    fun text(requestedDisplayId: Int, text: String): Boolean {
        if (requestedDisplayId != displayId || text.isEmpty()) return false
        val events = runCatching {
            KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
        }.getOrNull() ?: return false
        return runCatching {
            events.forEach { inject(it) }
            true
        }.getOrDefault(false)
    }

    fun launch(requestedDisplayId: Int, packageName: String, activity: String): Boolean {
        if (requestedDisplayId != displayId || packageName.isBlank()) return false
        val manager = context.packageManager
        val intent = if (activity.isNotBlank()) {
            Intent().setComponent(ComponentName(packageName, activity))
        } else {
            manager.getLaunchIntentForPackage(packageName)
        }
        val component = intent?.component ?: return false
        return runCatching {
            val process = ProcessBuilder(
                "/system/bin/am",
                "start",
                "--user",
                "0",
                "--display",
                displayId.toString(),
                "-f",
                "0x10000000",
                "-n",
                component.flattenToString(),
            ).redirectErrorStream(true).start()
            val output = StringBuilder()
            val drain = Thread {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
                }
            }
            drain.start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            drain.join(2_000)
            val text = output.toString()
            finished && process.exitValue() == 0 && !text.contains("Error:") && !text.contains("Exception")
        }.getOrDefault(false)
    }

    fun destroy() {
        stop()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun currentResult(): DisplayStartResult = DisplayStartResult(
        displayId = displayId,
        width = width,
        height = height,
        density = density,
        trusted = trusted,
        backend = DisplayBackendKind.Shizuku,
    )

    private fun valid(requestedDisplayId: Int, x: Int, y: Int): Boolean =
        requestedDisplayId == displayId && x in 0 until width && y in 0 until height

    private fun createDisplay(w: Int, h: Int, d: Int, flags: Int): VirtualDisplay {
        val manager = context.getSystemService(DisplayManager::class.java)
            ?: error("DisplayManager is unavailable")
        @Suppress("DEPRECATION")
        val result = manager.createVirtualDisplay("Focus Virtual", w, h, d, reader?.surface, flags)
        return result ?: error("createVirtualDisplay returned null")
    }

    private fun onFrame(source: ImageReader) {
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        try {
            synchronized(lock) {
                if (reader !== source) return
                val plane = image.planes.firstOrNull() ?: return
                val pixelStride = plane.pixelStride.coerceAtLeast(1)
                val paddedWidth = (plane.rowStride / pixelStride).coerceAtLeast(image.width)
                var bitmap = frame
                if (bitmap == null || bitmap.width != paddedWidth || bitmap.height != image.height) {
                    bitmap?.recycle()
                    bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                    frame = bitmap
                }
                plane.buffer.rewind()
                bitmap.copyPixelsFromBuffer(plane.buffer)
                frameWidth = image.width.coerceAtMost(bitmap.width)
                frameHeight = image.height.coerceAtMost(bitmap.height)
                frameReady = true
                lock.notifyAll()
            }
        } catch (_: Throwable) {
        } finally {
            image.close()
        }
    }

    private fun touch(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int) {
        val duration = durationMs.coerceIn(0, 5_000)
        val down = SystemClock.uptimeMillis()
        motion(down, down, MotionEvent.ACTION_DOWN, startX.toFloat(), startY.toFloat(), 1f)
        try {
            if (duration > 0) {
                var elapsed = 0
                while (elapsed < duration) {
                    SystemClock.sleep(16)
                    elapsed += 16
                    val fraction = elapsed.toFloat() / duration
                    motion(
                        down,
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_MOVE,
                        startX + (endX - startX) * fraction,
                        startY + (endY - startY) * fraction,
                        1f,
                    )
                }
            }
        } finally {
            motion(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, endX.toFloat(), endY.toFloat(), 0f)
        }
    }

    private fun motion(down: Long, whenAt: Long, action: Int, x: Float, y: Float, pressure: Float) {
        val event = MotionEvent.obtain(
            down,
            whenAt,
            action,
            x,
            y,
            pressure,
            1f,
            0,
            1f,
            1f,
            0,
            0,
        )
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            inject(event)
        } finally {
            event.recycle()
        }
    }

    private fun keyEvent(down: Long, whenAt: Long, action: Int, keyCode: Int): KeyEvent = KeyEvent(
        down,
        whenAt,
        action,
        keyCode,
        0,
        0,
        KeyCharacterMap.VIRTUAL_KEYBOARD,
        0,
        0,
        InputDevice.SOURCE_KEYBOARD,
    )

    private fun inject(event: InputEvent) {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
            .invoke(event, displayId)
        val managerClass = runCatching {
            Class.forName("android.hardware.input.InputManagerGlobal")
        }.getOrElse {
            Class.forName("android.hardware.input.InputManager")
        }
        val manager = managerClass.getMethod("getInstance").invoke(null)
        val accepted = managerClass
            .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            .invoke(manager, event, 1) as? Boolean ?: false
        check(accepted) { "input injection rejected" }
    }

    private fun applyImePolicy(id: Int) {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "window") as? IBinder ?: return@runCatching
            val manager = Class.forName("android.view.IWindowManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder) ?: return@runCatching
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.javaClass
                    .getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(manager, id, 0)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.javaClass
                    .getMethod("setShouldShowIme", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    .invoke(manager, id, true)
            }
        }
    }

    private fun acquireWakeLocks(id: Int) {
        val manager = context.getSystemService(PowerManager::class.java) ?: return
        displayWake = runCatching {
            val method = PowerManager::class.java.getDeclaredMethod(
                "newWakeLock",
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(manager, PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "focus:display", id) as PowerManager.WakeLock
        }.getOrNull()
        displayWake?.setReferenceCounted(false)
        runCatching { displayWake?.acquire() }
        cpuWake = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "focus:display:cpu").apply {
            setReferenceCounted(false)
        }
        runCatching { cpuWake?.acquire() }
    }

    private fun stopLocked() {
        frameReady = false
        displayWake?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        displayWake = null
        cpuWake?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        cpuWake = null
        reader?.setOnImageAvailableListener(null, null)
        display?.release()
        display = null
        reader?.close()
        reader = null
        frame?.recycle()
        frame = null
        frameWidth = 0
        frameHeight = 0
        displayId = 0
        trusted = false
        frameThread?.quitSafely()
        frameThread = null
        lock.notifyAll()
    }

    private fun baseFlags(): Int =
        flag("PUBLIC") or
            flag("PRESENTATION") or
            flag("OWN_CONTENT_ONLY") or
            flag("SUPPORTS_TOUCH") or
            flag("ROTATES_WITH_CONTENT") or
            flag("DESTROY_CONTENT_ON_REMOVAL")

    private fun trustedFlags(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 0
        var flags = flag("TRUSTED") or
            flag("OWN_DISPLAY_GROUP") or
            flag("ALWAYS_UNLOCKED") or
            flag("TOUCH_FEEDBACK_DISABLED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            flags = flags or flag("OWN_FOCUS") or flag("DEVICE_DISPLAY_GROUP")
        }
        return flags
    }

    private fun flag(name: String): Int = runCatching {
        val field = DisplayManager::class.java.getDeclaredField("VIRTUAL_DISPLAY_FLAG_$name")
        field.isAccessible = true
        field.getInt(null)
    }.getOrDefault(0)
}
