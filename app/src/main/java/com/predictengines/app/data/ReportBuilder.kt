package com.predictengines.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class Breakdown(val name: String, val correct: Int, val wrong: Int) {
    val total: Int get() = correct + wrong
    val hitRatePercent: Int get() = if (total == 0) 0 else Math.round(correct * 100.0 / total).toInt()
}

/**
 * One Monday-Sunday week. [weekStart] is null for fixtures with no usable date.
 * [pending] = played fixtures' consensus picks that haven't been ticked or crossed yet (current league only).
 */
data class WeekReport(
    val weekStart: LocalDate?,
    val correct: Int,
    val wrong: Int,
    val pending: Int,
    val bySource: List<Breakdown>,
    val byMarket: List<Breakdown>,
    val records: List<PredictionRecord>
) {
    val total: Int get() = correct + wrong
    val hitRatePercent: Int get() = if (total == 0) 0 else Math.round(correct * 100.0 / total).toInt()
}

object ReportBuilder {

    private val SOURCE_ORDER = listOf("Consensus", "Brymo", "Poisson", "Stats")

    fun weekStartOf(date: String): LocalDate? =
        DateUtils.parse(date)?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Played fixtures' consensus picks still waiting for a tick or cross, per week. */
    fun pending(
        fixtures: List<FixtureItem>,
        marks: Map<String, PredictionRecord>,
        leagueId: String,
        now: LocalDateTime = LocalDateTime.now()
    ): Map<LocalDate?, Int> {
        val out = HashMap<LocalDate?, Int>()
        for (fx in fixtures) {
            val summary = fx.summary ?: continue
            val home = fx.home ?: continue
            val away = fx.away ?: continue
            if (!DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now)) continue
            val open = summary.consensus.picks.count {
                PredictionRecord.idOf(leagueId, fx.fixture.date, home, away, "Consensus", it.key) !in marks
            }
            if (open > 0) out.merge(weekStartOf(fx.fixture.date), open, Int::plus)
        }
        return out
    }

    /** Newest week first; the "no date" group last. */
    fun weeks(records: Collection<PredictionRecord>, pending: Map<LocalDate?, Int> = emptyMap()): List<WeekReport> {
        val byWeek = records.groupBy { weekStartOf(it.date) }
        val keys = (byWeek.keys + pending.keys).distinct()
        return keys.map { start ->
            val recs = (byWeek[start] ?: emptyList()).sortedWith(compareBy({ it.date }, { it.home }, { it.source }))
            WeekReport(
                weekStart = start,
                correct = recs.count { it.correct },
                wrong = recs.count { !it.correct },
                pending = pending[start] ?: 0,
                bySource = breakdown(recs, { it.source }, SOURCE_ORDER),
                byMarket = breakdown(recs, { it.group }, emptyList()),
                records = recs
            )
        }.sortedWith(compareByDescending<WeekReport> { it.weekStart != null }.thenByDescending { it.weekStart })
    }

    private fun breakdown(
        recs: List<PredictionRecord>,
        keyOf: (PredictionRecord) -> String,
        order: List<String>
    ): List<Breakdown> =
        recs.groupBy(keyOf).map { (name, rs) -> Breakdown(name, rs.count { it.correct }, rs.count { !it.correct }) }
            .sortedWith(compareBy({ order.indexOf(it.name).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { -it.total }))

    fun weekLabel(start: LocalDate?): String {
        if (start == null) return "No date"
        val end = start.plusDays(6)
        val day = DateTimeFormatter.ofPattern("d", Locale.getDefault())
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val full = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        return if (start.month == end.month && start.year == end.year) {
            "${start.format(day)} – ${end.format(full)}"
        } else if (start.year == end.year) {
            "${start.format(dayMonth)} – ${end.format(full)}"
        } else {
            "${start.format(full)} – ${end.format(full)}"
        }
    }

    /** Plain-text report for sharing. */
    fun toText(week: WeekReport): String {
        val sb = StringBuilder()
        sb.append("Priwin · week ").append(weekLabel(week.weekStart)).append('\n')
        sb.append("Marked: ").append(week.total)
            .append(" · Correct: ").append(week.correct)
            .append(" · Wrong: ").append(week.wrong)
        if (week.total > 0) sb.append(" · Hit rate: ").append(week.hitRatePercent).append('%')
        if (week.pending > 0) sb.append("\nStill to mark: ").append(week.pending)
        sb.append('\n')
        if (week.bySource.isNotEmpty()) {
            sb.append("\nBy model\n")
            week.bySource.forEach { sb.append("  ").append(line(it)).append('\n') }
        }
        if (week.byMarket.isNotEmpty()) {
            sb.append("\nBy market\n")
            week.byMarket.forEach { sb.append("  ").append(line(it)).append('\n') }
        }
        if (week.records.isNotEmpty()) {
            sb.append("\nPredictions\n")
            week.records.forEach {
                sb.append(if (it.correct) "  ✓ " else "  ✗ ")
                    .append(it.home).append(" v ").append(it.away)
                    .append(" (").append(it.leagueName).append("): ")
                    .append(it.label).append(" [").append(it.source).append("]\n")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun line(b: Breakdown) = "${b.name}: ${b.correct}/${b.total} (${b.hitRatePercent}%)"
}
