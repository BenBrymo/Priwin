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
    val awayCards: Int? = null
) {
    val isEmpty: Boolean
        get() = corners == null && cards == null && first == null &&
            homeCorners == null && awayCorners == null && homeCards == null && awayCards == null
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
                    awayCards = if (e.has("ak")) e.getInt("ak") else null
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
