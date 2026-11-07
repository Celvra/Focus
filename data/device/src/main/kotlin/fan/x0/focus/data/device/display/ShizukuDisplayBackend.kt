package fan.x0.focus.data.device.display

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ShizukuDisplayBackend(
    private val context: Context,
) : DisplayBackend {
    override val kind: DisplayBackendKind = DisplayBackendKind.Shizuku

    private val json = Json { ignoreUnknownKeys = true }
    private var service: IFocusDisplay? = null
    private var connection: ServiceConnection? = null
    private var displayId = 0

    fun available(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override suspend fun start(spec: VirtualDisplaySpec): DisplayStartResult {
        val client = bind()
        val raw = client.start(spec.width, spec.height, spec.density)
        val result = parseStart(raw)
        displayId = result.displayId
        return result.copy(backend = DisplayBackendKind.Shizuku)
    }

    override suspend fun stop() {
        runCatching { service?.stop() }
        displayId = 0
    }

    override suspend fun status(): DisplayStatus {
        val client = service ?: return DisplayStatus()
        val raw = runCatching { client.status() }.getOrNull() ?: return DisplayStatus()
        return parseStatus(raw)
    }

    override suspend fun capture(quality: Int, maxWidth: Int): ByteArray? = withContext(Dispatchers.IO) {
        val client = service ?: return@withContext null
        val descriptor = runCatching { client.capture("jpeg", quality, maxWidth) }.getOrNull()
            ?: return@withContext null
        runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }.getOrNull()
    }

    override suspend fun tap(displayId: Int, x: Int, y: Int, durationMs: Int): Boolean =
        runCatching { service?.tap(displayId, x, y, durationMs) == true }.getOrDefault(false)

    override suspend fun swipe(
        displayId: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int,
    ): Boolean = runCatching {
        service?.swipe(displayId, startX, startY, endX, endY, durationMs) == true
    }.getOrDefault(false)

    override suspend fun key(displayId: Int, keyCode: Int): Boolean =
        runCatching { service?.key(displayId, keyCode) == true }.getOrDefault(false)

    override suspend fun text(displayId: Int, text: String): Boolean =
        runCatching { service?.text(displayId, text) == true }.getOrDefault(false)

    override suspend fun launch(displayId: Int, packageName: String, activity: String): Boolean =
        runCatching { service?.launch(displayId, packageName, activity) == true }.getOrDefault(false)

    override fun release() {
        val args = serviceArgs()
        val active = connection
        service = null
        connection = null
        displayId = 0
        if (active != null) {
            runCatching { Shizuku.unbindUserService(args, active, true) }
        }
    }

    private suspend fun bind(): IFocusDisplay {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        return withTimeout(12_000) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    val args = serviceArgs()
                    val listener = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            val client = IFocusDisplay.Stub.asInterface(binder)
                            if (client == null) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(IllegalStateException("empty display binder"))
                                }
                                return
                            }
                            service = client
                            if (continuation.isActive) continuation.resume(client)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            service = null
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("display service disconnected"))
                            }
                        }
                    }
                    connection = listener
                    runCatching { Shizuku.bindUserService(args, listener) }
                        .onFailure { error ->
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                }
            }
        }
    }

    private fun serviceArgs(): Shizuku.UserServiceArgs {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return Shizuku.UserServiceArgs(
            ComponentName(context.packageName, FocusDisplayService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("display")
            .tag("focus-display")
            .version(1)
            .debuggable(debuggable)
    }

    private fun parseStart(raw: String): DisplayStartResult {
        val value = json.parseToJsonElement(raw).jsonObject
        if (value["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            error(value["error"]?.jsonPrimitive?.contentOrNull ?: "display start failed")
        }
        return DisplayStartResult(
            displayId = value["displayId"]?.jsonPrimitive?.intOrNull ?: 0,
            width = value["width"]?.jsonPrimitive?.intOrNull ?: 0,
            height = value["height"]?.jsonPrimitive?.intOrNull ?: 0,
            density = value["density"]?.jsonPrimitive?.intOrNull ?: 0,
            trusted = value["trusted"]?.jsonPrimitive?.booleanOrNull ?: false,
            backend = DisplayBackendKind.Shizuku,
        )
    }

    private fun parseStatus(raw: String): DisplayStatus {
        val value = json.parseToJsonElement(raw).jsonObject
        if (value["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            return DisplayStatus(error = value["error"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
        return DisplayStatus(
            running = value["running"]?.jsonPrimitive?.booleanOrNull ?: false,
            displayId = value["displayId"]?.jsonPrimitive?.intOrNull ?: 0,
            width = value["width"]?.jsonPrimitive?.intOrNull ?: 0,
            height = value["height"]?.jsonPrimitive?.intOrNull ?: 0,
            density = value["density"]?.jsonPrimitive?.intOrNull ?: 0,
            trusted = value["trusted"]?.jsonPrimitive?.booleanOrNull ?: false,
            frameReady = value["frameReady"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }
}
