package com.predictengines.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The picks that were on the weekly lists (Top 20 picks, Handicap, Over 1.5) while their game was still to be
 * played. They are written down the moment a list is built, so each one can be judged and reported after the game
 * even if the list changes later. Kept in list_picks.json (included in Backup & Restore).
 */
class ListPicksStore(context: Context) {

    data class Entry(
        val source: String,       // "Top 20", "Handicap" or "Over 1.5"
        val leagueId: String,
        val leagueName: String,
        val date: String,
        val home: String,
        val away: String,
        val key: String,
        val label: String,
        val group: String,
        val time: String = ""
    ) {
        val id: String get() = PredictionRecord.idOf(leagueId, date, home, away, source, key)
    }

    private val file = File(context.applicationContext.filesDir, "list_picks.json")

    fun load(): Map<String, Entry> = try {
        if (!file.exists()) emptyMap() else {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            val out = LinkedHashMap<String, Entry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val e = Entry(
                    o.getString("s"), o.getString("l"), o.getString("ln"), o.getString("d"),
                    o.getString("h"), o.getString("a"), o.getString("k"), o.getString("lb"), o.getString("g"),
                    o.optString("tm", "")
                )
                out[e.id] = e
            }
            out
        }
    } catch (e: Exception) {
        emptyMap()
    }

    /** Adds [entries] to what is already written down. Returns true when something new was added. */
    fun add(entries: Collection<Entry>): Boolean {
        val all = LinkedHashMap(load())
        var changed = false
        for (e in entries) {
            val old = all[e.id]
            if (old == null || (old.time.isBlank() && e.time.isNotBlank())) { all[e.id] = e; changed = true }
        }
        if (!changed) return false
        val arr = JSONArray()
        for (e in all.values) {
            arr.put(
                JSONObject().put("s", e.source).put("l", e.leagueId).put("ln", e.leagueName).put("d", e.date)
                    .put("h", e.home).put("a", e.away).put("k", e.key).put("lb", e.label).put("g", e.group).put("tm", e.time)
            )
        }
        try {
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    /** The recorded list picks of one game. */
    fun forGame(all: Map<String, Entry>, leagueId: String, date: String, home: String, away: String): List<Entry> {
        val prefix = "$leagueId|$date|$home|$away|"
        return all.values.filter { it.id.startsWith(prefix) }
    }

    companion object {
        const val TOP = "Top 20"
        const val HANDICAP = "Handicap"
        const val OVER15 = "Over 1.5"
        val SOURCES = listOf(TOP, HANDICAP, OVER15)
    }
}
