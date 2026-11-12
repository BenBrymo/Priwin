package com.predictengines.app.data

import java.text.Normalizer

/**
 * Key players as a CSV: one row per player, one file for a whole league (or several).
 *   league, team, player, position, status, return_date
 * Only team and player are needed; the other columns are optional and may be in any order, with any extra columns.
 */
object PlayersCsv {
    /** [league] is what the file said (an id, a name, or blank for the open league); position and status are null when the cell was empty. */
    data class Row(val league: String, val team: String, val player: String, val position: PlayerPosition?, val status: PlayerStatus?, val returnDate: String)

    data class Parsed(val rows: List<Row>, val skipped: Int, val error: String? = null)

    /** A row whose league and team were matched to the app's own names. */
    data class Resolved(val leagueId: String, val team: String, val player: String, val position: PlayerPosition?, val status: PlayerStatus?, val returnDate: String)

    /** What an import would do, shown before anything is saved. */
    data class Preview(
        val rows: Int, val teams: Int, val added: Int, val updated: Int, val removedIfReplace: Int,
        val unmatchedTeams: List<String>, val unknownLeagues: List<String>, val skipped: Int
    )

    data class Merge(val players: List<KeyPlayer>, val added: Int, val updated: Int, val removed: List<KeyPlayer>)

    private val LEAGUE = listOf("league", "leagueid", "competition", "division", "comp")
    private val TEAM = listOf("team", "club", "teamname", "clubname", "squad")
    private val PLAYER = listOf("player", "playername", "name", "fullname")
    private val POSITION = listOf("position", "pos", "role")
    private val STATUS = listOf("status", "availability", "fitness", "injurystatus")
    private val RETURN = listOf("returndate", "return", "expectedreturn", "expectedback", "backdate", "returns", "backon")

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun index(h: List<String>, keys: List<String>): Int {
        for (k in keys) { val i = h.indexOf(k); if (i >= 0) return i }
        return -1
    }

    fun parse(text: String): Parsed {
        val table = CsvParser.table(text)
        if (table.isEmpty()) return Parsed(emptyList(), 0, "The file is empty.")
        // the header may sit below a title line
        var headerAt = -1; var h = emptyList<String>()
        for (i in 0 until minOf(table.size, 15)) {
            val cand = table[i].map { norm(it) }
            if (index(cand, TEAM) >= 0 && index(cand, PLAYER) >= 0) { headerAt = i; h = cand; break }
        }
        if (headerAt < 0) return Parsed(emptyList(), 0, "Couldn't find the columns. The first row needs at least \"team\" and \"player\" (optional: league, position, status, return_date).")
        val li = index(h, LEAGUE); val ti = index(h, TEAM); val pi = index(h, PLAYER)
        val posi = index(h, POSITION); val si = index(h, STATUS); val ri = index(h, RETURN)
        var skipped = 0
        val rows = ArrayList<Row>()
        for (r in table.drop(headerAt + 1)) {
            fun cell(i: Int) = if (i >= 0) r.getOrNull(i).orEmpty().trim() else ""
            val team = cell(ti); val player = cell(pi)
            if (team.isBlank() || player.isBlank()) { if (r.any { it.isNotBlank() }) skipped++; continue }
            rows += Row(cell(li), team, player, position(cell(posi)), status(cell(si)), date(cell(ri)))
        }
        return Parsed(rows, skipped)
    }

    fun position(raw: String): PlayerPosition? {
        val s = norm(raw)
        if (s.isEmpty()) return null
        return when {
            s in setOf("gk", "goalkeeper", "keeper", "goalie", "g") -> PlayerPosition.GK
            s in setOf("def", "defender", "defence", "defense", "d", "cb", "lb", "rb", "wb", "centreback", "centerback", "fullback", "leftback", "rightback") -> PlayerPosition.DEF
            s in setOf("mid", "midfielder", "midfield", "m", "cm", "dm", "am", "cdm", "cam", "lm", "rm") -> PlayerPosition.MID
            s in setOf("fwd", "forward", "striker", "attacker", "fw", "f", "st", "cf", "winger", "lw", "rw", "attack", "ss") -> PlayerPosition.FWD
            else -> null
        }
    }

    fun status(raw: String): PlayerStatus? {
        val s = norm(raw)
        if (s.isEmpty()) return null
        return when {
            s.startsWith("avail") || s in setOf("fit", "ok", "active", "healthy") -> PlayerStatus.AVAILABLE
            s.startsWith("doubt") || s == "unsure" || s == "knock" -> PlayerStatus.DOUBTFUL
            s.startsWith("injur") || s == "out" || s == "hurt" -> PlayerStatus.INJURED
            s.startsWith("susp") || s == "banned" || s == "ban" -> PlayerStatus.SUSPENDED
            else -> null
        }
    }

    /** yyyy-MM-dd, dd/MM/yyyy and dd-MM-yyyy (day first) are understood; anything else is left blank. */
    fun date(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2}).*""").matchEntire(s)?.let { return fmt(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        Regex("""(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}).*""").matchEntire(s)?.let { return fmt(it.groupValues[3], it.groupValues[2], it.groupValues[1]) }
        return ""
    }

    private fun fmt(y: String, m: String, d: String): String {
        val mm = m.toInt(); val dd = d.toInt()
        return if (mm in 1..12 && dd in 1..31) "%s-%02d-%02d".format(y, mm, dd) else ""
    }

    private fun nameKey(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    /**
     * Applies [rows] to [existing]. A player already there (same league, team and name) is updated: a position, status or return
     * date the file gives replaces the old one, an empty cell keeps it, and the photo and id stay. New players are added.
     * With [replaceTeams], players of a team that appears in the file but not in it are removed (teams not in the file are untouched).
     */
    fun merge(existing: List<KeyPlayer>, rows: List<Resolved>, replaceTeams: Boolean, newId: () -> String = { java.util.UUID.randomUUID().toString() }): Merge {
        val result = ArrayList(existing)
        var added = 0; var updated = 0
        val seen = HashSet<String>()
        for (r in rows) {
            val key = "${r.leagueId}|${r.team}|${nameKey(r.player)}"
            if (!seen.add(key)) continue // the same player twice in the file: the first row counts
            val i = result.indexOfFirst { it.leagueId == r.leagueId && it.team == r.team && nameKey(it.name) == nameKey(r.player) }
            if (i >= 0) {
                val old = result[i]
                val st = r.status ?: old.status
                var p = old.copy(
                    position = r.position ?: old.position,
                    status = st,
                    returnDate = if (r.status != null || r.returnDate.isNotBlank()) (if (st == PlayerStatus.AVAILABLE) "" else r.returnDate.ifBlank { old.returnDate }) else old.returnDate
                )
                if (r.status != null && st != PlayerStatus.SUSPENDED) p = p.copy(banGames = 0, banFrom = "")
                if (p != old) { result[i] = p; updated++ }
            } else {
                val st = r.status ?: PlayerStatus.AVAILABLE
                result += KeyPlayer(newId(), r.leagueId, r.team, r.player.trim(), r.position ?: PlayerPosition.MID, st, if (st == PlayerStatus.AVAILABLE) "" else r.returnDate)
                added++
            }
        }
        val removed = ArrayList<KeyPlayer>()
        if (replaceTeams) {
            val fileTeams = rows.map { it.leagueId to it.team }.toSet()
            val inFile = rows.map { "${it.leagueId}|${it.team}|${nameKey(it.player)}" }.toSet()
            val it2 = result.iterator()
            while (it2.hasNext()) {
                val p = it2.next()
                if ((p.leagueId to p.team) in fileTeams && "${p.leagueId}|${p.team}|${nameKey(p.name)}" !in inFile) { removed += p; it2.remove() }
            }
        }
        return Merge(result, added, updated, removed)
    }

    private fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Every key player as a CSV that [parse] reads back (the league column holds the league id). */
    fun export(players: List<KeyPlayer>): String {
        val sb = StringBuilder("league,team,player,position,status,return_date\n")
        for (p in players.sortedWith(compareBy({ it.leagueId }, { it.team }, { it.position.ordinal }, { it.name }))) {
            sb.append(listOf(p.leagueId, p.team, p.name, p.position.short, p.status.label, p.returnDate).joinToString(",") { q(it) }).append('\n')
        }
        return sb.toString()
    }
}
