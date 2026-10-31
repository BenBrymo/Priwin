package com.predictengines.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File

enum class FirstScorer { HOME, AWAY }

/**
 * Things a score can't say. Optional: filled in, they let corners, cards and first-to-score picks settle themselves.
 * [corners] and [cards] are the match totals (the picks are judged on these); the per-team numbers are what you
 * enter, and the totals are their sums when both teams are given.
 */
data class MatchExtras(
    val corners: Int? = null,
    val cards: Int? = null,
    val first: FirstScorer? = null,
    val homeCorners: Int? = null,
    val awayCorners: Int? = null,
    val homeCards: Int? = null,
    val awayCards: Int? = null,
    /** Red cards of each team from the matches file (already included in the card totals). */
    val homeReds: Int? = null,
    val awayReds: Int? = null
) {
    val isEmpty: Boolean
        get() = corners == null && cards == null && first == null &&
            homeCorners == null && awayCorners == null && homeCards == null && awayCards == null

    /** What you entered wins, field by field; anything you left empty is filled from [file] (the matches file). */
    fun orElse(file: MatchExtras?): MatchExtras {
        if (file == null) return this
        val hc = homeCorners ?: file.homeCorners
        val ac = awayCorners ?: file.awayCorners
        val hk = homeCards ?: file.homeCards
        val ak = awayCards ?: file.awayCards
        return MatchExtras(
            corners = if (hc != null && ac != null) hc + ac else corners ?: file.corners,
            cards = if (hk != null && ak != null) hk + ak else cards ?: file.cards,
            first = first ?: file.first,
            homeCorners = hc, awayCorners = ac, homeCards = hk, awayCards = ak,
            homeReds = homeReds ?: file.homeReds, awayReds = awayReds ?: file.awayReds
        )
    }

    companion object {
        /** Builds extras from the file's per-team numbers. Cards = yellow + red (a missing red count counts as 0). */
        fun fromFile(hc: Int?, ac: Int?, hy: Int?, ay: Int?, hr: Int?, ar: Int?): MatchExtras? {
            val hk = hy?.let { it + (hr ?: 0) }
            val ak = ay?.let { it + (ar ?: 0) }
            val x = MatchExtras(
                corners = if (hc != null && ac != null) hc + ac else null,
                cards = if (hk != null && ak != null) hk + ak else null,
                homeCorners = hc, awayCorners = ac, homeCards = hk, awayCards = ak,
                homeReds = hr, awayReds = ar
            )
            return if (x.isEmpty) null else x
        }
    }
}

/**
 * Two small files in the app's private storage:
 *  - result_extras.json: the extras above, per fixture;
 *  - settled_fixtures.json: which fixtures have already been settled (their stats updated and picks ticked)
 *    and with which result, so nothing is applied twice and an edited score is applied again properly.
 */
class ResultExtrasStore(context: Context) {

    private val dir = context.applicationContext.filesDir
    private val extrasFile = File(dir, "result_extras.json")
    private val settledFile = File(dir, "settled_fixtures.json")

    fun loadExtras(): Map<String, MatchExtras> = try {
        if (!extrasFile.exists()) emptyMap() else {
            val o = JSONObject(extrasFile.readText(Charsets.UTF_8))
            o.keys().asSequence().associateWith { k ->
                val e = o.getJSONObject(k)
                MatchExtras(
                    corners = if (e.has("c")) e.getInt("c") else null,
                    cards = if (e.has("k")) e.getInt("k") else null,
                    first = if (e.has("f")) runCatching { FirstScorer.valueOf(e.getString("f")) }.getOrNull() else null,
                    homeCorners = if (e.has("hc")) e.getInt("hc") else null,
                    awayCorners = if (e.has("ac")) e.getInt("ac") else null,
                    homeCards = if (e.has("hk")) e.getInt("hk") else null,
                    awayCards = if (e.has("ak")) e.getInt("ak") else null,
                    homeReds = if (e.has("hr")) e.getInt("hr") else null,
                    awayReds = if (e.has("ar")) e.getInt("ar") else null
                )
            }
        }
    } catch (e: Exception) {
        emptyMap()
    }

    fun saveExtras(map: Map<String, MatchExtras>) {
        val o = JSONObject()
        for ((k, v) in map) {
            if (v.isEmpty) continue
            val e = JSONObject()
            v.corners?.let { e.put("c", it) }
            v.cards?.let { e.put("k", it) }
            v.first?.let { e.put("f", it.name) }
            v.homeCorners?.let { e.put("hc", it) }
            v.awayCorners?.let { e.put("ac", it) }
            v.homeCards?.let { e.put("hk", it) }
            v.awayCards?.let { e.put("ak", it) }
            v.homeReds?.let { e.put("hr", it) }
            v.awayReds?.let { e.put("ar", it) }
            o.put(k, e)
        }
        write(extrasFile, o)
    }

    fun loadSettled(): Map<String, String> = try {
        if (!settledFile.exists()) emptyMap() else {
            val o = JSONObject(settledFile.readText(Charsets.UTF_8))
            o.keys().asSequence().associateWith { o.getString(it) }
        }
    } catch (e: Exception) {
        emptyMap()
    }

    fun saveSettled(map: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        write(settledFile, o)
    }

    private fun write(f: File, o: JSONObject) {
        try {
            if (o.length() == 0) f.delete() else f.writeText(o.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            // best effort
        }
    }
}

/** The result a fixture was settled with, e.g. "2-1|9|4|HOME" (used to notice an edited score). */
fun settleSignature(homeGoals: Int, awayGoals: Int, x: MatchExtras?): String =
    "$homeGoals-$awayGoals|${x?.corners ?: ""}|${x?.cards ?: ""}|${x?.first?.name ?: ""}"
