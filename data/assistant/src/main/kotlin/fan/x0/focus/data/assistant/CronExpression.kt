package fan.x0.focus.data.assistant

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CronExpression private constructor(
    private val seconds: Set<Int>,
    private val minutes: Set<Int>,
    private val hours: Set<Int>,
    private val daysOfMonth: Set<Int>,
    private val months: Set<Int>,
    private val daysOfWeek: Set<Int>,
    private val domRestricted: Boolean,
    private val dowRestricted: Boolean,
) {
    fun next(afterMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        var candidate = Instant.ofEpochMilli(afterMillis)
            .atZone(zoneId)
            .plusSeconds(1)
            .withNano(0)
        val sortedSeconds = seconds.sorted()
        var guard = 0
        while (guard++ < MAX_ITERATIONS) {
            if (candidate.monthValue !in months) {
                candidate = candidate.plusMonths(1)
                    .withDayOfMonth(1)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0)
                continue
            }
            if (!dayMatches(candidate)) {
                candidate = candidate.plusDays(1)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0)
                continue
            }
            if (candidate.hour !in hours) {
                candidate = candidate.plusHours(1)
                    .withMinute(0)
                    .withSecond(0)
                continue
            }
            if (candidate.minute !in minutes) {
                candidate = candidate.plusMinutes(1).withSecond(0)
                continue
            }
            val second = sortedSeconds.firstOrNull { it >= candidate.second }
            if (second == null) {
                candidate = candidate.plusMinutes(1).withSecond(0)
                continue
            }
            if (second != candidate.second) {
                candidate = candidate.withSecond(second)
            }
            return candidate.toInstant().toEpochMilli()
        }
        return null
    }

    private fun dayMatches(time: ZonedDateTime): Boolean {
        val dom = time.dayOfMonth in daysOfMonth
        val dow = (time.dayOfWeek.value % 7) in daysOfWeek
        return when {
            domRestricted && dowRestricted -> dom || dow
            domRestricted -> dom
            dowRestricted -> dow
            else -> true
        }
    }

    companion object {
        private const val MAX_ITERATIONS = 5 * 366 * 24 * 60

        private val MONTHS = mapOf(
            "JAN" to 1,
            "FEB" to 2,
            "MAR" to 3,
            "APR" to 4,
            "MAY" to 5,
            "JUN" to 6,
            "JUL" to 7,
            "AUG" to 8,
            "SEP" to 9,
            "OCT" to 10,
            "NOV" to 11,
            "DEC" to 12,
        )

        private val DAYS = mapOf(
            "SUN" to 0,
            "MON" to 1,
            "TUE" to 2,
            "WED" to 3,
            "THU" to 4,
            "FRI" to 5,
            "SAT" to 6,
        )

        fun validate(expression: String): String? = try {
            parse(expression)
            null
        } catch (e: IllegalArgumentException) {
            e.message ?: "Invalid cron expression"
        }

        fun parse(expression: String): CronExpression {
            val normalized = macro(expression.trim()).uppercase()
            val parts = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size != 5 && parts.size != 6) {
                throw IllegalArgumentException("Cron needs 5 or 6 fields")
            }
            val six = parts.size == 6
            val offset = if (six) 1 else 0
            val seconds = if (six) parseField(parts[0], 0, 59, emptyMap()) else setOf(0)
            val minutes = parseField(parts[offset], 0, 59, emptyMap())
            val hours = parseField(parts[offset + 1], 0, 23, emptyMap())
            val dom = parseField(parts[offset + 2], 1, 31, emptyMap())
            val month = parseField(parts[offset + 3], 1, 12, MONTHS)
            val dowRaw = parseField(parts[offset + 4], 0, 7, DAYS)
            val dow = dowRaw.map { if (it == 7) 0 else it }.toSet()
            return CronExpression(
                seconds = seconds,
                minutes = minutes,
                hours = hours,
                daysOfMonth = dom,
                months = month,
                daysOfWeek = dow,
                domRestricted = parts[offset + 2] != "*" && parts[offset + 2] != "?",
                dowRestricted = parts[offset + 4] != "*" && parts[offset + 4] != "?",
            )
        }

        private fun macro(value: String): String = when (value.lowercase()) {
            "@yearly", "@annually" -> "0 0 1 1 *"
            "@monthly" -> "0 0 1 * *"
            "@weekly" -> "0 0 * * 0"
            "@daily", "@midnight" -> "0 0 * * *"
            "@hourly" -> "0 * * * *"
            else -> value
        }

        private fun parseField(
            text: String,
            min: Int,
            max: Int,
            names: Map<String, Int>,
        ): Set<Int> {
            if (text == "*" || text == "?") return (min..max).toSet()
            val result = sortedSetOf<Int>()
            text.split(',').forEach { part ->
                val segment = part.trim()
                if (segment.isEmpty()) throw IllegalArgumentException("Empty cron field")
                val stepSplit = segment.split('/')
                if (stepSplit.size > 2) throw IllegalArgumentException("Invalid step: $segment")
                val step = stepSplit.getOrNull(1)?.toIntOrNull() ?: 1
                if (step <= 0) throw IllegalArgumentException("Step must be positive")
                val base = stepSplit[0]
                val range = when {
                    base == "*" -> min..max
                    base.contains('-') -> {
                        val bounds = base.split('-', limit = 2)
                        val start = value(bounds[0], names)
                        val end = value(bounds[1], names)
                        if (start > end) throw IllegalArgumentException("Invalid range: $base")
                        start..end
                    }
                    else -> {
                        val start = value(base, names)
                        if (stepSplit.size == 2) start..max else start..start
                    }
                }
                var current = range.first
                while (current <= range.last) {
                    if (current in min..max) result += current
                    current += step
                }
            }
            if (result.isEmpty()) throw IllegalArgumentException("Cron field has no values")
            return result
        }

        private fun value(text: String, names: Map<String, Int>): Int =
            names[text] ?: text.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid cron value: $text")
    }
}
