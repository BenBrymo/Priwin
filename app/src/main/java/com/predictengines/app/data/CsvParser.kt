package com.predictengines.app.data

import java.util.Locale

/**
 * Forgiving CSV reader for the three file types the app imports:
 *  - matches (played results):  Date,Time,HomeTeam,AwayTeam,FTHG,FTAG
 *  - fixtures (upcoming games): Date,Time,HomeTeam,AwayTeam
 *  - stats (statschecker):      market,season,rank,team,stat_value,games,percentage
 *
 * Files may have ANY number of extra columns, in any order: only the columns the app needs are found by
 * their header names and everything else is ignored, so nobody has to delete columns first. Header names
 * are matched ignoring case, spaces and punctuation ("Home Team", "home_team" and "HomeTeam" are the
 * same), common alternatives are accepted (FTHG / HomeGoals / Home Score ...), and the header does not have
 * to be on the first line (title lines above it are skipped). The delimiter (comma / semicolon / tab) is
 * auto-detected, quotes and a UTF-8 BOM are handled, and header-less files fall back to positional columns.
 */
object CsvParser {

    /** [note] says which columns were used, for the "Loaded ..." message after an import. */
    data class Parsed<T>(val rows: List<T>, val skipped: Int, val error: String? = null, val note: String? = null)

    // Alternatives are listed by priority: if a file has both "FTHG" and "HG", FTHG is used.
    private val DATE_KEYS = listOf("date", "matchdate", "kickoffdate", "datetime", "kickoff", "matchdatetime", "gamedate")
    private val TIME_KEYS = listOf("time", "kickofftime", "kotime", "starttime", "matchtime")
    private val HOME_KEYS = listOf("hometeam", "home", "hometeamname", "homename", "team1", "homeclub", "hometeamfull")
    private val AWAY_KEYS = listOf("awayteam", "away", "awayteamname", "awayname", "team2", "awayclub", "awayteamfull")
    private val HG_KEYS = listOf(
        "fthg", "homegoals", "homescore", "hg", "hgoals", "homeft", "homegoalsft", "fthomegoals",
        "fulltimehomegoals", "homegoalsfulltime", "goalshome", "scorehome", "ftgh"
    )
    private val AG_KEYS = listOf(
        "ftag", "awaygoals", "awayscore", "ag", "agoals", "awayft", "awaygoalsft", "ftawaygoals",
        "fulltimeawaygoals", "awaygoalsfulltime", "goalsaway", "scoreaway", "ftga"
    )

    /** Header words that mean a column is NOT the full-time score (half-time goals, expected goals ...). */
    private val NOT_FULL_TIME = listOf("half", "ht", "xg", "1st", "2nd", "first", "second", "pen", "corner", "shot", "against", "conceded", "avg", "average", "odds")

    private const val HEADER_SCAN_ROWS = 15

    /** Where the needed columns are, and which row is the header. */
    private class MatchCols(
        val headerRow: Int, val date: Int, val time: Int, val home: Int, val away: Int, val hg: Int, val ag: Int,
        val labels: List<String>
    )

    private fun pick(header: List<String>, keys: List<String>): Int {
        for (k in keys) {
            val i = header.indexOf(k)
            if (i >= 0) return i
        }
        return -1
    }

    /** Last resort for score columns with unusual names, e.g. "Home Goals (Full Time)". */
    private fun fuzzyGoals(header: List<String>, side: String): Int = header.indexOfFirst { h ->
        h.contains(side) && (h.contains("goal") || h.contains("score")) && NOT_FULL_TIME.none { h.contains(it) }
    }

    /** Finds the header row (within the first rows) that has home, away and both goal columns. */
    private fun findMatchHeader(table: List<List<String>>): MatchCols? {
        for (r in 0 until minOf(table.size, HEADER_SCAN_ROWS)) {
            val h = table[r].map { normalizeHeader(it) }
            val home = pick(h, HOME_KEYS)
            val away = pick(h, AWAY_KEYS)
            if (home < 0 || away < 0 || home == away) continue
            val hg = pick(h, HG_KEYS).takeIf { it >= 0 } ?: fuzzyGoals(h, "home")
            val ag = pick(h, AG_KEYS).takeIf { it >= 0 } ?: fuzzyGoals(h, "away")
            if (hg < 0 || ag < 0 || hg == ag) continue
            val date = pick(h, DATE_KEYS)
            val time = pick(h, TIME_KEYS).takeIf { it != date } ?: -1
            return MatchCols(r, date, time, home, away, hg, ag, table[r])
        }
        return null
    }

    /** True if some early row has home and away team columns (a fixtures file, or a matches file missing goals). */
    private fun hasTeamHeader(table: List<List<String>>): Boolean =
        (0 until minOf(table.size, HEADER_SCAN_ROWS)).any { r ->
            val h = table[r].map { normalizeHeader(it) }
            pick(h, HOME_KEYS) >= 0 && pick(h, AWAY_KEYS) >= 0
        }

    private fun usedNote(labels: List<String>, used: List<Int>): String {
        val names = used.filter { it >= 0 }.distinct().map { labels.getOrNull(it).orEmpty().trim() }.filter { it.isNotEmpty() }
        val ignored = labels.withIndex().count { (i, l) -> l.isNotBlank() && i !in used }
        return "used ${names.joinToString(", ")}" + if (ignored > 0) " · ignored $ignored other columns" else ""
    }

    fun parseMatches(text: String): Parsed<Match> {
        val table = readTable(text)
        if (table.isEmpty()) return Parsed(emptyList(), 0, "The file is empty.")

        val cols = findMatchHeader(table)
        val dateI: Int
        val homeI: Int
        val awayI: Int
        val hgI: Int
        val agI: Int
        val dataRows: List<List<String>>
        var note: String? = null
        if (cols != null) {
            dateI = cols.date; homeI = cols.home; awayI = cols.away; hgI = cols.hg; agI = cols.ag
            dataRows = table.drop(cols.headerRow + 1)
            note = usedNote(cols.labels, listOf(cols.date, cols.time, cols.home, cols.away, cols.hg, cols.ag))
        } else if (hasTeamHeader(table)) {
            return Parsed(
                emptyList(), 0,
                "No goal columns found. A matches file needs FTHG and FTAG " +
                    "(or HomeGoals / AwayGoals). Did you pick a fixtures file by mistake?"
            )
        } else {
            val n = table.first().size
            when {
                n >= 6 -> { dateI = 0; homeI = 2; awayI = 3; hgI = 4; agI = 5 }
                n == 5 -> { dateI = 0; homeI = 1; awayI = 2; hgI = 3; agI = 4 }
                n == 4 -> { dateI = -1; homeI = 0; awayI = 1; hgI = 2; agI = 3 }
                else -> return Parsed(
                    emptyList(), 0,
                    "Couldn't recognise the columns. Expected: Date,Time,HomeTeam,AwayTeam,FTHG,FTAG"
                )
            }
            dataRows = table
        }

        val out = ArrayList<Match>()
        var skipped = 0
        for (row in dataRows) {
            val home = row.getOrNull(homeI).orEmpty().trim()
            val away = row.getOrNull(awayI).orEmpty().trim()
            val hg = parseGoals(row.getOrNull(hgI))
            val ag = parseGoals(row.getOrNull(agI))
            if (home.isEmpty() || away.isEmpty() || hg == null || ag == null) {
                skipped++
                continue
            }
            out.add(Match(stripTime(row.getOrNull(dateI).orEmpty().trim()), cleanName(home), cleanName(away), hg, ag))
        }
        if (out.isEmpty()) {
            return Parsed(emptyList(), skipped, "No valid match rows found ($skipped rows skipped).")
        }
        val dates = normalizeDates(out.map { it.date }, dayFirst = true)
        return Parsed(out.mapIndexed { i, m -> m.copy(date = dates[i]) }, skipped, note = note)
    }

    private val MARKET_KEYS = setOf("market", "stat", "statname", "category")
    private val SEASON_KEYS = setOf("season")
    private val RANK_KEYS = setOf("rank")
    private val TEAM_KEYS = setOf("team", "teamname", "club")
    private val VALUE_KEYS = setOf("statvalue", "value", "count")
    private val GAMES_KEYS = setOf("games", "played", "matches", "gp")
    private val PCT_KEYS = setOf("percentage", "percent", "pct", "rate")

    /**
     * Stats file (statschecker export): market,season,rank,team,stat_value,games,percentage[,team_url].
     * Needs a header row. Extra columns are ignored; repeated header lines and bad rows are skipped.
     */
    fun parseStats(text: String): Parsed<StatRow> {
        val table = readTable(text)
        if (table.isEmpty()) return Parsed(emptyList(), 0, "The file is empty.")
        val header = table.first().map { normalizeHeader(it) }
        val marketI = indexOfAny(header, MARKET_KEYS)
        val teamI = indexOfAny(header, TEAM_KEYS)
        val valueI = indexOfAny(header, VALUE_KEYS)
        val gamesI = indexOfAny(header, GAMES_KEYS)
        val pctI = indexOfAny(header, PCT_KEYS)
        val seasonI = indexOfAny(header, SEASON_KEYS)
        val rankI = indexOfAny(header, RANK_KEYS)
        if (marketI < 0 || teamI < 0 || (valueI < 0 && pctI < 0)) {
            return Parsed(
                emptyList(), 0,
                "Couldn't recognise the columns. A stats file needs a header row like: " +
                    "market,season,rank,team,stat_value,games,percentage"
            )
        }

        val out = ArrayList<StatRow>()
        var skipped = 0
        for (row in table.drop(1)) {
            val market = row.getOrNull(marketI).orEmpty().trim()
            val team = row.getOrNull(teamI).orEmpty().trim()
            val value = parseNumber(row.getOrNull(valueI))
            val games = parseNumber(row.getOrNull(gamesI))?.toInt() ?: 0
            val pct = parseNumber(row.getOrNull(pctI))
            if (market.isEmpty() || team.isEmpty() || (value == null && pct == null)) {
                skipped++
                continue
            }
            val v = value ?: (pct!! / 100.0 * games)
            out.add(
                StatRow(
                    market = market,
                    season = row.getOrNull(seasonI).orEmpty().trim(),
                    rank = parseNumber(row.getOrNull(rankI))?.toInt(),
                    team = cleanName(team),
                    value = v,
                    games = games,
                    percent = pct
                )
            )
        }
        if (out.isEmpty()) return Parsed(emptyList(), skipped, "No valid stat rows found ($skipped rows skipped).")
        return Parsed(out, skipped)
    }

    private fun parseNumber(raw: String?): Double? =
        raw?.trim()?.removeSuffix("%")?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0 }

    fun parseFixtures(text: String): Parsed<Fixture> {
        val table = readTable(text)
        if (table.isEmpty()) return Parsed(emptyList(), 0, "The file is empty.")

        var headerRow = -1
        var dateI = -1
        var timeI = -1
        var homeI = -1
        var awayI = -1
        var labels: List<String> = emptyList()
        for (r in 0 until minOf(table.size, HEADER_SCAN_ROWS)) {
            val h = table[r].map { normalizeHeader(it) }
            val home = pick(h, HOME_KEYS)
            val away = pick(h, AWAY_KEYS)
            if (home < 0 || away < 0 || home == away) continue
            headerRow = r; homeI = home; awayI = away
            dateI = pick(h, DATE_KEYS)
            timeI = pick(h, TIME_KEYS).takeIf { it != dateI } ?: -1
            labels = table[r]
            break
        }

        val dataRows: List<List<String>>
        var note: String? = null
        if (headerRow >= 0) {
            dataRows = table.drop(headerRow + 1)
            note = usedNote(labels, listOf(dateI, timeI, homeI, awayI))
        } else {
            val cols = table.first().size
            when {
                cols >= 4 && table.first().getOrNull(1).orEmpty().contains(':') -> { dateI = 0; timeI = 1; homeI = 2; awayI = 3 }
                cols >= 3 -> { dateI = 0; timeI = -1; homeI = 1; awayI = 2 }
                cols == 3 -> { dateI = 0; timeI = -1; homeI = 1; awayI = 2 }
                cols == 2 -> { dateI = -1; timeI = -1; homeI = 0; awayI = 1 }
                else -> return Parsed(
                    emptyList(), 0,
                    "Couldn't recognise the columns. Expected: Date,Time,HomeTeam,AwayTeam"
                )
            }
            dataRows = table
        }

        val out = LinkedHashSet<Fixture>()
        var skipped = 0
        for (row in dataRows) {
            val home = row.getOrNull(homeI).orEmpty().trim()
            val away = row.getOrNull(awayI).orEmpty().trim()
            if (home.isEmpty() || away.isEmpty() || home.equals(away, ignoreCase = true)) {
                skipped++
                continue
            }
            var date = row.getOrNull(dateI).orEmpty().trim()
            var time = row.getOrNull(timeI).orEmpty().trim()
            // Support a combined "2026-10-17 15:00" / "2026-10-17T15:00" date column.
            if (time.isEmpty() && (date.contains(' ') || date.contains('T'))) {
                val parts = date.split(' ', 'T', limit = 2)
                date = parts[0]
                time = parts.getOrElse(1) { "" }.take(5)
            }
            out.add(Fixture(date, time, cleanName(home), cleanName(away)))
        }
        if (out.isEmpty()) {
            return Parsed(emptyList(), skipped, "No valid fixture rows found ($skipped rows skipped).")
        }
        val list = out.toList()
        val dates = normalizeDates(list.map { it.date }, dayFirst = true)
        return Parsed(list.mapIndexed { i, f -> f.copy(date = dates[i]) }, skipped, note = note)
    }

    // ---- helpers ---------------------------------------------------------------------------

    private val DATE_WITH_TIME = Regex("^(\\d[\\d/.\\-]*\\d)[ T]\\d{1,2}:\\d{2}.*$")

    /** "29/09/2025 19:00" (date and time in one column) -> "29/09/2025". */
    private fun stripTime(date: String): String = DATE_WITH_TIME.matchEntire(date)?.groupValues?.get(1) ?: date

    private val NUMERIC_DATE = Regex("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2}|\\d{4})$")

    /** Collapses accidentally doubled words, e.g. "Ipswich Town Town" -> "Ipswich Town". */
    private fun cleanName(name: String): String {
        val out = ArrayList<String>()
        for (w in name.trim().split(Regex("\\s+"))) {
            if (out.isEmpty() || !out.last().equals(w, ignoreCase = true)) out.add(w)
        }
        return out.joinToString(" ")
    }

    /**
     * Converts numeric dates (08/21/2026, 21/08/2026, 5.9.26 ...) to yyyy-MM-dd.
     *
     * The app uses fixed formats instead of guessing:
     *  - matches files   : Day / Month / Year   ([dayFirst] = true)
     *  - fixtures files  : Day / Month / Year   ([dayFirst] = true), time may follow in the same cell
     * If a date is impossible in the expected order (e.g. a month of 17), the other order is used,
     * so a few stray rows in the opposite style still sort correctly. ISO dates (yyyy-MM-dd) are kept.
     */
    private fun normalizeDates(raw: List<String>, dayFirst: Boolean): List<String> {
        return raw.map { s ->
            val m = NUMERIC_DATE.matchEntire(s.trim()) ?: return@map s
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            var day = if (dayFirst) a else b
            var month = if (dayFirst) b else a
            if (month !in 1..12) {
                val t = day
                day = month
                month = t
            }
            var year = m.groupValues[3].toInt()
            if (m.groupValues[3].length == 2) year += 2000
            if (month !in 1..12 || day !in 1..31) s
            else String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
        }
    }

    private fun parseGoals(raw: String?): Int? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val n = s.toIntOrNull() ?: s.toDoubleOrNull()?.toInt() ?: return null
        return if (n < 0) null else n
    }

    private fun normalizeHeader(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    private fun indexOfAny(header: List<String>, keys: Set<String>): Int =
        header.indexOfFirst { it in keys }

    private fun readTable(text: String): List<List<String>> {
        val clean = text.removePrefix("\uFEFF")
        val lines = clean.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList()
        val delimiter = detectDelimiter(lines)
        return lines.map { splitLine(it, delimiter) }
    }

    /** Looks at the first lines (not just the first, which may be a title) and picks the likeliest delimiter. */
    private fun detectDelimiter(lines: List<String>): Char {
        val sample = lines.take(10)
        val best = listOf(',', ';', '\t').maxByOrNull { d -> sample.sumOf { line -> line.count { it == d } } } ?: ','
        return if (sample.sumOf { line -> line.count { it == best } } == 0) ',' else best
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                out.add(sb.toString().trim())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }
}
