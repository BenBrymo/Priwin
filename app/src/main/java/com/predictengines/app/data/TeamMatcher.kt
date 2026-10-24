package com.predictengines.app.data

import java.text.Normalizer
import java.util.Locale

/**
 * Maps a team name from a fixtures file onto the spelling used in the matches file,
 * so "Atlético Madrid", "Atletico Madrid FC" and "Ath Madrid" style differences don't
 * stop a fixture from being predicted.
 */
object TeamMatcher {

    private val STOP_WORDS = setOf(
        "fc", "cf", "afc", "sc", "ac", "as", "ssc", "fk", "sk", "if", "bk", "the", "club", "calcio"
    )
    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Links the user made inside the app, as pairs of raw names (see [NameLink]). */
    @Volatile
    private var userLinks: List<Pair<String, String>> = emptyList()

    /** Normalised alias -> the key of its club (see [ClubAliases] and the user's links). */
    @Volatile
    private var aliasIndex: Map<String, String> = buildIndex(emptyList())

    /** Replaces the user's links. Call before loading data, and again after a link is added or removed. */
    fun setUserLinks(links: List<Pair<String, String>>) {
        userLinks = links
        aliasIndex = buildIndex(links)
    }

    /** True if [name] appears in the built-in alias list or in one of the user's links. */
    fun isKnownAlias(name: String): Boolean = aliasIndex.containsKey(normalizeRaw(name))

    /**
     * Every name in a group (built-in line or user link) is the same club. Groups that share a name are
     * merged, so linking "Spurs" to a name that is already in a built-in line joins that whole line.
     */
    private fun buildIndex(links: List<Pair<String, String>>): Map<String, String> {
        val ids = HashMap<String, Int>()
        val names = ArrayList<String>()
        val parent = ArrayList<Int>()
        fun idOf(n: String): Int = ids.getOrPut(n) {
            names.add(n)
            parent.add(parent.size)
            names.size - 1
        }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) if (ra < rb) parent[rb] = ra else parent[ra] = rb
        }
        val groups = ClubAliases.GROUPS + links.map { listOf(it.first, it.second) }
        for (group in groups) {
            val members = group.map { normalizeRaw(it) }.filter { it.isNotEmpty() }.map { idOf(it) }
            for (k in 1 until members.size) union(members[0], members[k])
        }
        // The key of a club is its earliest-listed name, so keys stay stable.
        return names.indices.associate { i -> names[i] to names[find(i)] }
    }

    /**
     * The comparison key for a team name: lower case, no accents, no FC/CF/AS tags, and every known
     * spelling of a club ("Man City", "Manchester City FC") collapsed to one key.
     */
    fun normalize(name: String): String {
        val n = normalizeRaw(name)
        return aliasIndex[n] ?: n
    }

    /**
     * [known] names ordered by how likely they are to be the same club as [name], best first
     * (names that already match come first). Used by the in-app "link this name" picker.
     */
    fun suggest(name: String, known: List<String>): List<String> {
        val raw = normalizeRaw(name)
        val key = normalize(name)
        val rawTokens = raw.split(" ").filter { it.isNotEmpty() }.toSet()
        fun score(k: String): Double {
            val kRaw = normalizeRaw(k)
            val kKey = normalize(k)
            if (kKey == key) return 2.0
            val kTokens = kRaw.split(" ").filter { it.isNotEmpty() }.toSet()
            val shared = rawTokens.intersect(kTokens).size
            val overlap = if (shared == 0) 0.0 else shared.toDouble() / maxOf(rawTokens.size, kTokens.size)
            val prefix = if (raw.length >= 3 && (kRaw.startsWith(raw) || raw.startsWith(kRaw))) 0.8 else 0.0
            return maxOf(similarity(raw, kRaw), overlap, prefix)
        }
        return known.filter { it != name }.sortedWith(compareByDescending<String> { score(it) }.thenBy { it })
    }

    /**
     * True if two names are confidently the same club: same key, or the same words where each
     * short word starts / abbreviates the long one ("Newcastle Utd" / "Newcastle United").
     * Deliberately stricter than [resolve], which also uses looser matching.
     */
    fun sameTeam(a: String, b: String): Boolean = sameKeys(normalize(a), normalize(b))

    /** [sameTeam] on keys that are already [normalize]d (so callers can compute them once). */
    fun sameKeys(ka: String, kb: String): Boolean {
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka.replace(" ", "") == kb.replace(" ", "")) return true
        val ta = ka.split(" ")
        val tb = kb.split(" ")
        return ta.size == tb.size && (abbreviationMatch(ta, tb) || abbreviationMatch(tb, ta))
    }

    private fun normalizeRaw(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
            .replace('ı', 'i').replace('ø', 'o').replace("æ", "ae")
            .replace("ß", "ss").replace('ł', 'l').replace('đ', 'd')
            .replace("&", " and ")
        val ascii = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(MARKS, "")
        val tokens = ascii.replace(NON_ALNUM, " ").trim().split(" ").filter { it.isNotEmpty() }
        val stripped = tokens.filter { it !in STOP_WORDS }
        return (if (stripped.isEmpty()) tokens else stripped).joinToString(" ")
    }

    /** Returns the matching name from [known], or null if nothing is close enough. */
    fun resolve(name: String, known: List<String>): String? {
        if (known.isEmpty()) return null
        known.firstOrNull { it == name }?.let { return it }

        val target = normalize(name)
        if (target.isEmpty()) return null
        val normalized = known.map { it to normalize(it) }

        val compactTarget = target.replace(" ", "")
        normalized.firstOrNull { it.second.replace(" ", "") == compactTarget }?.let { return it.first }

        // Token containment ("Real Madrid CF" vs "Real Madrid"), only if unambiguous.
        val targetTokens = target.split(" ")
        val contained = normalized.filter { pair ->
            minOf(pair.second.length, target.length) >= 4 && containmentMatch(targetTokens, pair.second.split(" "))
        }
        if (contained.size == 1) return contained[0].first

        // Abbreviations ("Man City" vs "Manchester City", "Newcastle Utd" vs "Newcastle United"):
        // same number of words, each short word being the start of / letters from the long one.
        val abbreviated = normalized.filter { pair ->
            val kt = pair.second.split(" ")
            val tt = target.split(" ")
            kt.size == tt.size && (abbreviationMatch(tt, kt) || abbreviationMatch(kt, tt))
        }
        if (abbreviated.size == 1) return abbreviated[0].first

        // Fuzzy fallback for small spelling differences.
        var best: String? = null
        var bestScore = 0.0
        for (pair in normalized) {
            val score = similarity(target, pair.second)
            if (score > bestScore) {
                bestScore = score
                best = pair.first
            }
        }
        // Only for longer names: with short ones a single letter is a different club
        // (Celta/Ceuta, Angers/Rangers, Juventus/Juventud).
        return if (bestScore >= 0.85 && target.length >= 9) best else null
    }

    /** Words that can sit in front of a club name without making it a different club. */
    private val GENERIC_PREFIX = setOf("real", "olympique", "deportivo", "borussia", "atletico", "club")

    /**
     * One name contains the other: "Leeds" in "Leeds United", "Sociedad" in "Real Sociedad". Words after the
     * shared part are fine; words before it must be generic, otherwise "Rangers" would match
     * "Queens Park Rangers" and "Eindhoven" would match "PSV Eindhoven".
     */
    private fun containmentMatch(a: List<String>, b: List<String>): Boolean {
        val (short, long) = if (a.size <= b.size) a to b else b to a
        if (!long.containsAll(short)) return false
        val start = long.indexOf(short.first())
        return long.take(start).all { it in GENERIC_PREFIX }
    }

    /** Multi-word names only: single words are too easy to confuse (Porto / Portsmouth, Lille / Lillestrom). */
    private fun abbreviationMatch(short: List<String>, long: List<String>): Boolean {
        if (short.size < 2) return false
        return short.indices.all { i -> wordMatches(short[i], long[i]) }
    }

    private fun wordMatches(short: String, long: String): Boolean {
        if (short == long || long.startsWith(short)) return true
        // Short clipped forms keep the word's letters in order: "utd" in "united", "weds" in "wednesday".
        if (short.length !in 3..4 || short[0] != long[0]) return false
        var i = 0
        for (c in long) if (i < short.length && short[i] == c) i++
        return i == short.length
    }

    private fun similarity(a: String, b: String): Double {
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
