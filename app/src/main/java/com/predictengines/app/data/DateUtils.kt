package com.predictengines.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Date helpers for fixture sorting and display. Day-first formats are assumed for dd/MM/yy styles. */
object DateUtils {

    private val formatters = listOf(
        "dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "d/M/yy",
        "yyyy-MM-dd", "yyyy/MM/dd",
        "dd-MM-yyyy", "d-M-yyyy",
        "dd.MM.yyyy", "d.M.yyyy", "dd.MM.yy"
    ).map { DateTimeFormatter.ofPattern(it, Locale.ROOT) }

    fun parse(raw: String): LocalDate? {
        val s = raw.trim().take(10)
        if (s.isEmpty()) return null
        for (f in formatters) {
            try {
                return LocalDate.parse(s, f)
            } catch (e: DateTimeParseException) {
                // try next format
            }
        }
        return null
    }

    private fun minutesOf(time: String): Int? {
        val p = time.trim().split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].take(2).toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** Roughly how long a match lasts, used to decide when a fixture counts as played. */
    private const val MATCH_MINUTES = 120L

    /**
     * True once a fixture is over: kickoff + ~2h has passed, or, when there's no time, the day has passed.
     * Unparseable dates are never "played".
     */
    fun isPlayed(date: String, time: String, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val d = parse(date) ?: return false
        val m = minutesOf(time) ?: return d.isBefore(now.toLocalDate())
        return d.atTime(m / 60 % 24, m % 60).plusMinutes(MATCH_MINUTES).isBefore(now)
    }

    /** Sort key: unparseable dates go last, original order is preserved by the stable sort. */
    fun sortKey(date: String, time: String): Long {
        val d = parse(date) ?: return Long.MAX_VALUE
        return d.toEpochDay() * 2000L + (minutesOf(time) ?: 0)
    }

    /** Heading for a group of fixtures, e.g. "Sat, 17 Oct 2026". */
    fun dayLabel(date: String): String {
        val d = parse(date) ?: return if (date.isBlank()) "Date TBC" else date
        return d.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
    }

    /** Kickoff label with the year, e.g. "Sat 17 Oct 2026 · 15:00". */
    fun kickoffLabelWithYear(date: String, time: String): String {
        val d = parse(date)
        val day = d?.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())) ?: date
        return listOf(day, time).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Short kickoff label, e.g. "Sat 17 Oct · 15:00". */
    fun kickoffLabel(date: String, time: String): String {
        val d = parse(date)
        val day = d?.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) ?: date
        return listOf(day, time).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
