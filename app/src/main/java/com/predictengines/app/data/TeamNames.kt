package com.predictengines.app.data

/**
 * Makes one club have one name inside a loaded file. If a matches file has both "Man City" and
 * "Manchester City", or "Roma" and "AS Roma", every game is rewritten to a single spelling (the one
 * used most), so the models see one team with its full record instead of two half-records.
 * The same is done for the stats file. Fixture names are matched to these canonical names separately
 * by [TeamMatcher.resolve].
 */
object TeamNames {

    /** Maps every raw name to the canonical spelling of its club. [counts] = how often each name appears. */
    fun canonicalMap(counts: Map<String, Int>): Map<String, String> {
        val names = counts.keys.toList()
        val keys = names.map { TeamMatcher.normalize(it) }
        val parent = IntArray(names.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                if (TeamMatcher.sameKeys(keys[i], keys[j])) parent[find(j)] = find(i)
            }
        }
        val out = HashMap<String, String>()
        for (members in names.indices.groupBy { find(it) }.values) {
            val canonical = members.map { names[it] }
                .sortedWith(compareByDescending<String> { counts[it] ?: 0 }.thenBy { it.length }.thenBy { it })
                .first()
            members.forEach { out[names[it]] = canonical }
        }
        return out
    }

    fun unifyMatches(matches: List<Match>): List<Match> {
        val counts = HashMap<String, Int>()
        for (m in matches) {
            counts.merge(m.homeTeam, 1, Int::plus)
            counts.merge(m.awayTeam, 1, Int::plus)
        }
        val map = canonicalMap(counts)
        return matches.map { m ->
            val h = map[m.homeTeam] ?: m.homeTeam
            val a = map[m.awayTeam] ?: m.awayTeam
            if (h == m.homeTeam && a == m.awayTeam) m else m.copy(homeTeam = h, awayTeam = a)
        }
    }

    fun unifyStats(rows: List<StatRow>): List<StatRow> {
        val counts = HashMap<String, Int>()
        for (r in rows) counts.merge(r.team, 1, Int::plus)
        val map = canonicalMap(counts)
        return rows.map { r ->
            val t = map[r.team] ?: r.team
            if (t == r.team) r else r.copy(team = t)
        }
    }
}
