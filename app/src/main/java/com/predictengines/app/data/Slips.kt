package com.predictengines.app.data

/** Bet slips: a week's ranked picks of one list, cut into slips of 8 different games. */
object Slips {
    const val SIZE = 8

    /** [hit] is how often this kind of pick has landed in your record (0-100), [safe] the same corrected for few picks; null = no record yet. */
    data class Leg(val rank: Int, val pick: TopPick, val hit: Double?, val safe: Double?)

    data class Slip(val number: Int, val legs: List<Leg>, val size: Int = SIZE) {
        val isShort: Boolean get() = legs.size < size
        private val known: List<Leg> get() = legs.filter { it.hit != null }
        val withRecord: Int get() = known.size
        /** Every leg with a record landing (hit rates multiplied, 0-100); null when no leg has a record yet. */
        val chance: Double? get() = known.takeIf { it.isNotEmpty() }?.fold(1.0) { a, l -> a * (l.hit ?: 0.0) / 100.0 }?.times(100.0)
        val cautious: Double? get() = known.takeIf { it.isNotEmpty() }?.fold(1.0) { a, l -> a * (l.safe ?: 0.0) / 100.0 }?.times(100.0)
        val expected: Double get() = known.sumOf { (it.hit ?: 0.0) / 100.0 }
    }

    /**
     * [ranked] is the week's picks, strongest first, with their rank. Only the strongest pick of each game is kept,
     * so no slip holds the same game twice; then every [size] picks make a slip (the last may be short).
     */
    fun build(ranked: List<Pair<Int, TopPick>>, size: Int = SIZE, chance: (TopPick) -> Pair<Double, Double>?): List<Slip> {
        val seen = HashSet<String>()
        val legs = ranked.filter { (_, tp) -> seen.add("${tp.leagueId}|${tp.date}|${tp.home}|${tp.away}") }
            .map { (rank, tp) -> chance(tp).let { c -> Leg(rank, tp, c?.first, c?.second) } }
        return legs.chunked(size).mapIndexed { i, l -> Slip(i + 1, l, size) }
    }
}
