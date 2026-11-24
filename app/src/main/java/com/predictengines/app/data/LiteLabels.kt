package com.predictengines.app.data

/** Plain-language labels for Priwin Picks: no model jargon, just how strong a pick is and why. */
object LiteLabels {
    enum class Grade(val label: String) { STRONG("Strong"), GOOD("Good"), RISKY("Risky") }

    /**
     * Strong: this kind of pick has landed 65%+ in the record (and held up after allowing for few picks).
     * Good: 55%+. Risky: below that. A pick with no record yet is Good only when the models are very confident.
     */
    fun grade(tp: TopPick): Grade {
        val p = tp.pick
        return when {
            p.hit > 0 -> if (p.hit >= 65 && p.hitSafe >= 50) Grade.STRONG else if (p.hit >= 55) Grade.GOOD else Grade.RISKY
            p.backers.isNotEmpty() && p.averageConfidence >= 75 -> Grade.GOOD
            else -> Grade.RISKY
        }
    }

    /** The pick's name without the technical reasons. */
    fun pickName(tp: TopPick): String = tp.pick.label.substringBefore(" · ")

    /** One plain line: how many of the models back it and how often this kind of pick has landed. */
    fun why(tp: TopPick): String {
        val p = tp.pick
        val parts = ArrayList<String>()
        if (p.modelCount > 0 && p.backers.isNotEmpty()) parts += "${p.backers.size} of ${p.modelCount} models back it"
        if (p.hit > 0) parts += "this kind of pick has landed ${Math.round(p.hit)}% of the time in our record"
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }
}
