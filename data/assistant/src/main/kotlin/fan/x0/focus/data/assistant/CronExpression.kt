package fan.x0.focus.data.assistant

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CronExpression private constructor(
    private val seconds: Set<Int>,
    private val minutes: Set<Int>,
    private val hours: Set<Int>,
    private val daysOfMonth: Set<Int>,
    private val lastDayOfMonth: Boolean,
    private val lastDayOffset: Int,
    private val lastWeekdayOfMonth: Boolean,
    private val nearestWeekdays: Set<Int>,
    private val months: Set<Int>,
    private val daysOfWeek: Set<Int>,
    private val lastWeekdays: Set<Int>,
    private val nthWeekdays: List<Pair<Int, Int>>,
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
        val dom = dayOfMonthMatches(time)
        val dow = dayOfWeekMatches(time)
        return when {
            domRestricted && dowRestricted -> dom || dow
            domRestricted -> dom
            dowRestricted -> dow
            else -> true
        }
    }

    private fun dayOfMonthMatches(time: ZonedDateTime): Boolean {
        val day = time.dayOfMonth
        val length = time.toLocalDate().lengthOfMonth()
        if (day in daysOfMonth) return true
        if (lastDayOfMonth && day == length) return true
        if (lastDayOffset > 0 && day == length - lastDayOffset) return true
        if (lastWeekdayOfMonth && day == lastWeekday(time)) return true
        if (day in nearestWeekdays && day == nearestWeekday(time, day)) return true
        return false
    }

    private fun dayOfWeekMatches(time: ZonedDateTime): Boolean {
        val dow = time.dayOfWeek.value % 7
        val day = time.dayOfMonth
        val length = time.toLocalDate().lengthOfMonth()
        if (dow in daysOfWeek) return true
        if (dow in lastWeekdays && day + 7 > length) return true
        return nthWeekdays.any { (target, nth) ->
            dow == target && ((day - 1) / 7 + 1) == nth
        }
    }

    private fun nearestWeekday(time: ZonedDateTime, target: Int): Int {
        val length = time.toLocalDate().lengthOfMonth()
        val value = time.withDayOfMonth(target.coerceIn(1, length)).dayOfWeek.value % 7
        return when (value) {
            6 -> if (target == 1) 3 else target - 1
            0 -> if (target == length) target - 2 else target + 1
            else -> target
        }
    }

    private fun lastWeekday(time: ZonedDateTime): Int {
        var day = time.toLocalDate().lengthOfMonth()
        while (day > 0) {
            val dow = time.withDayOfMonth(day).dayOfWeek.value % 7
            if (dow != 0 && dow != 6) return day
            day--
        }
        return day
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
            val domText = parts[offset + 2]
            val month = parseField(parts[offset + 3], 1, 12, MONTHS)
            val dowText = parts[offset + 4]
            val dom = parseDayOfMonth(domText)
            val dow = parseDayOfWeek(dowText)
            return CronExpression(
                seconds = seconds,
                minutes = minutes,
                hours = hours,
                daysOfMonth = dom.values,
                lastDayOfMonth = dom.lastDay,
                lastDayOffset = dom.lastOffset,
                lastWeekdayOfMonth = dom.lastWeekday,
                nearestWeekdays = dom.nearestWeekdays,
                months = month,
                daysOfWeek = dow.values,
                lastWeekdays = dow.lastWeekdays,
                nthWeekdays = dow.nthWeekdays,
                domRestricted = !unrestricted(domText),
                dowRestricted = !unrestricted(dowText),
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

        private fun unrestricted(text: String): Boolean = text == "*" || text == "?"

        private data class DomRule(
            val values: Set<Int> = emptySet(),
            val lastDay: Boolean = false,
            val lastOffset: Int = 0,
            val lastWeekday: Boolean = false,
            val nearestWeekdays: Set<Int> = emptySet(),
        )

        private data class DowRule(
            val values: Set<Int> = emptySet(),
            val lastWeekdays: Set<Int> = emptySet(),
            val nthWeekdays: List<Pair<Int, Int>> = emptyList(),
        )

        private fun parseDayOfMonth(text: String): DomRule {
            if (text == "*" || text == "?") return DomRule(values = (1..31).toSet())
            var values = emptySet<Int>()
            var lastDay = false
            var lastOffset = 0
            var lastWeekday = false
            var nearest = emptySet<Int>()
            text.split(',').forEach { raw ->
                val part = raw.trim().uppercase()
                when {
                    part == "L" -> lastDay = true
                    part.startsWith("L-") -> {
                        lastOffset = part.removePrefix("L-").toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid L offset: $part")
                        if (lastOffset !in 1..30) throw IllegalArgumentException("Invalid L offset: $part")
                    }
                    part == "LW" -> lastWeekday = true
                    part.endsWith("W") -> {
                        val day = part.removeSuffix("W").toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid W day: $part")
                        if (day !in 1..31) throw IllegalArgumentException("Invalid W day: $part")
                        nearest = nearest + day
                    }
                    else -> values = values + parseField(part, 1, 31, emptyMap())
                }
            }
            return DomRule(values, lastDay, lastOffset, lastWeekday, nearest)
        }

        private fun parseDayOfWeek(text: String): DowRule {
            if (text == "*" || text == "?") return DowRule(values = (0..6).toSet())
            var values = emptySet<Int>()
            var last = emptySet<Int>()
            val nth = mutableListOf<Pair<Int, Int>>()
            text.split(',').forEach { raw ->
                val part = raw.trim().uppercase()
                when {
                    part == "L" -> values = values + 6
                    part.endsWith("L") -> {
                        val day = value(part.removeSuffix("L"), DAYS)
                        last = last + (if (day == 7) 0 else day)
                    }
                    part.contains('#') -> {
                        val sections = part.split('#')
                        if (sections.size != 2) throw IllegalArgumentException("Invalid # value: $part")
                        val day = value(sections[0], DAYS).let { if (it == 7) 0 else it }
                        val nthValue = sections[1].toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid # value: $part")
                        if (nthValue !in 1..5) throw IllegalArgumentException("Invalid # value: $part")
                        nth += day to nthValue
                    }
                    else -> values = values + parseField(part, 0, 7, DAYS).map { if (it == 7) 0 else it }
                }
            }
            return DowRule(values, last, nth)
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
