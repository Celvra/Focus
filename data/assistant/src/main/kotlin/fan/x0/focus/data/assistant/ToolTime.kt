package fan.x0.focus.data.assistant

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ToolTime {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val displayFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun iso(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(isoFormatter)

    fun display(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(displayFormatter)

    fun full(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(fullFormatter)

    fun parse(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        value.toLongOrNull()?.let { return it }
        runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching {
            LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }
        return runCatching {
            LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    fun startOfToday(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfTomorrow(): Long = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun daysFromNow(days: Int): Long =
        LocalDate.now(zone).plusDays(days.toLong()).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun now(): Long = System.currentTimeMillis()
}
