package fan.x0.focus.data.sources

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.util.TimeZone

@Single
class CalendarRepository(@Provided private val context: Context) {
    fun canRead(): Boolean = granted(Manifest.permission.READ_CALENDAR)

    fun canWrite(): Boolean = granted(Manifest.permission.WRITE_CALENDAR)

    suspend fun eventsBetween(start: Long, end: Long, limit: Int = 60): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            requireRead()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(start.toString())
                .appendPath(end.toString())
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        add(
                            CalendarEvent(
                                id = cursor.getLong(0),
                                title = cursor.getString(1).orEmpty(),
                                description = cursor.getString(2),
                                location = cursor.getString(3),
                                start = cursor.getLong(4),
                                end = cursor.getLong(5),
                                allDay = cursor.getInt(6) == 1,
                            ),
                        )
                    }
                }
            } ?: emptyList()
        }

    suspend fun search(query: String, days: Int = 60, limit: Int = 30): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            requireRead()
            val now = System.currentTimeMillis()
            val end = now + days.coerceIn(1, 365) * DAY_MS
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(end.toString())
                .build()
            val selection = "${CalendarContract.Instances.TITLE} LIKE ? OR ${CalendarContract.Instances.DESCRIPTION} LIKE ?"
            val args = arrayOf("%$query%", "%$query%")
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                ),
                selection,
                args,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        add(
                            CalendarEvent(
                                id = cursor.getLong(0),
                                title = cursor.getString(1).orEmpty(),
                                description = cursor.getString(2),
                                location = cursor.getString(3),
                                start = cursor.getLong(4),
                                end = cursor.getLong(5),
                                allDay = cursor.getInt(6) == 1,
                            ),
                        )
                    }
                }
            } ?: emptyList()
        }

    suspend fun createEvent(
        title: String,
        start: Long,
        end: Long,
        description: String?,
        location: String?,
    ): Long = withContext(Dispatchers.IO) {
        if (!canWrite()) throw SourceException(SourceError.PermissionDenied, "calendar write permission missing")
        if (title.isBlank()) throw SourceException(SourceError.Unsupported, "event title is empty")
        if (end <= start) throw SourceException(SourceError.Unsupported, "event end must be after start")
        val calendarId = writableCalendarId()
            ?: throw SourceException(SourceError.NotFound, "no writable calendar")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title.trim())
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw SourceException(SourceError.Storage, "calendar insert failed")
        ContentUris.parseId(uri)
    }

    suspend fun eventTitle(id: Long): String? = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext null
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
            arrayOf(CalendarContract.Events.TITLE),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    suspend fun deleteEvent(id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite()) throw SourceException(SourceError.PermissionDenied, "calendar write permission missing")
        context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0
    }

    private fun writableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun requireRead() {
        if (!canRead()) throw SourceException(SourceError.PermissionDenied, "calendar read permission missing")
    }

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
