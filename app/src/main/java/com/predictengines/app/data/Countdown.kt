package com.predictengines.app.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How near a kickoff is. [RED] = today (or on now), [ORANGE] = tomorrow, [AMBER] = within 3 days, [GREY] = later. */
enum class Urgency { GREY, AMBER, ORANGE, RED }

data class Countdown(val text: String, val urgency: Urgency, val withinHours: Boolean) {

    companion object {
        private fun kickoff(date: String, time: String): Pair<LocalDate, LocalDateTime?>? {
            val d = DateUtils.parse(date) ?: return null
            val p = time.trim().split(":")
            val h = p.getOrNull(0)?.toIntOrNull()
            val m = p.getOrNull(1)?.take(2)?.toIntOrNull()
            return d to if (h != null && m != null) d.atTime(h % 24, m) else null
        }

        /** The countdown for a fixture, or null when it is already played (no chip) or has no usable date. */
        fun of(date: String, time: String, now: LocalDateTime = LocalDateTime.now()): Countdown? {
            if (DateUtils.isPlayed(date, time, now)) return null
            val (d, at) = kickoff(date, time) ?: return null
            val days = ChronoUnit.DAYS.between(now.toLocalDate(), d)
            val clock = time.trim().take(5)
            return when {
                days <= 0L -> {
                    if (at == null) return Countdown("Today", Urgency.RED, false)
                    val mins = Duration.between(now, at).toMinutes()
                    when {
                        mins <= 0 -> Countdown("Live now", Urgency.RED, true)
                        mins < 60 -> Countdown("In ${mins}m", Urgency.RED, true)
                        mins < 6 * 60 -> Countdown(
                            String.format(Locale.ROOT, "In %dh %02dm", mins / 60, mins % 60), Urgency.RED, mins <= 180
                        )
                        else -> Countdown("Today $clock", Urgency.RED, false)
                    }
                }
                days == 1L -> Countdown(if (clock.isNotBlank()) "Tomorrow $clock" else "Tomorrow", Urgency.ORANGE, false)
                days <= 3L -> Countdown("In $days days", Urgency.AMBER, false)
                else -> Countdown("In $days days", Urgency.GREY, false)
            }
        }

        /** True when any of these (date, time) games kicks off today and isn't played yet. */
        fun anyToday(games: Collection<Pair<String, String>>, now: LocalDateTime = LocalDateTime.now()): Boolean =
            games.any { (d, t) -> of(d, t, now)?.urgency == Urgency.RED }

        /** One line for a week header, e.g. "Next kickoff in 2 days · 3 today"; null when nothing is still to be played. */
        fun summary(games: Collection<Pair<String, String>>, now: LocalDateTime = LocalDateTime.now()): Pair<String, Urgency>? {
            val upcoming = games.mapNotNull { (d, t) -> of(d, t, now)?.let { c -> Triple(DateUtils.sortKey(d, t), c, d) } }
            if (upcoming.isEmpty()) return null
            val next = upcoming.minByOrNull { it.first }!!
            val today = upcoming.count { it.second.urgency == Urgency.RED }
            val head = when (next.second.urgency) {
                Urgency.RED -> "Kicking off today"
                else -> "Next kickoff: " + next.second.text.replaceFirstChar { it.lowercase(Locale.ROOT) }
            }
            val tail = if (today > 0 && next.second.urgency == Urgency.RED) " · $today today" else ""
            return (head + tail) to next.second.urgency
        }
    }
}
