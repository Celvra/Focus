package fan.x0.focus.data.device.display

import android.content.Context
import android.os.Binder
import android.os.ParcelFileDescriptor
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FocusDisplayService(context: Context) : IFocusDisplay.Stub() {
    private val appUid = context.applicationInfo.uid
    private val session = DisplaySession(shellContext(context))

    override fun start(width: Int, height: Int, density: Int): String {
        authorize()
        return runCatching {
            val result = session.start(width, height, density)
            buildJsonObject {
                put("ok", true)
                put("displayId", result.displayId)
                put("width", result.width)
                put("height", result.height)
                put("density", result.density)
                put("trusted", result.trusted)
                put("backend", result.backend.name.lowercase())
            }.toString()
        }.getOrElse {
            session.stop()
            error(it)
        }
    }

    override fun stop(): String {
        authorize()
        return runCatching {
            session.stop()
            buildJsonObject { put("ok", true) }.toString()
        }.getOrElse { error(it) }
    }

    override fun status(): String {
        authorize()
        return runCatching {
            val status = session.status()
            buildJsonObject {
                put("ok", true)
                put("running", status.running)
                put("displayId", status.displayId)
                put("width", status.width)
                put("height", status.height)
                put("density", status.density)
                put("trusted", status.trusted)
                put("frameReady", status.frameReady)
            }.toString()
        }.getOrElse { error(it) }
    }

    override fun capture(format: String, quality: Int, maxWidth: Int): ParcelFileDescriptor {
        authorize()
        val bytes = session.capture(quality, maxWidth) ?: throw IllegalStateException("no frame")
        val pipe = ParcelFileDescriptor.createPipe()
        Thread({
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(bytes) }
            }
        }, "focus-display-capture").start()
        return pipe[0]
    }

    override fun tap(displayId: Int, x: Int, y: Int, durationMs: Int): Boolean {
        authorize()
        return session.tap(displayId, x, y, durationMs)
    }

    override fun swipe(
        displayId: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int,
    ): Boolean {
        authorize()
        return session.swipe(displayId, startX, startY, endX, endY, durationMs)
    }

    override fun key(displayId: Int, keyCode: Int): Boolean {
        authorize()
        return session.key(displayId, keyCode)
    }

    override fun text(displayId: Int, text: String): Boolean {
        authorize()
        return session.text(displayId, text)
    }

    override fun launch(displayId: Int, packageName: String, activity: String): Boolean {
        authorize()
        return session.launch(displayId, packageName, activity)
    }

    override fun destroy() {
        authorize()
        session.destroy()
    }

    private fun authorize() {
        val uid = Binder.getCallingUid()
        if (uid != 0 && uid != 2000 && uid != appUid) {
            throw SecurityException("unauthorized caller")
        }
    }

    private fun error(error: Throwable): String = buildJsonObject {
        put("ok", false)
        put("error", error.message ?: error.javaClass.simpleName)
    }.toString()
}
