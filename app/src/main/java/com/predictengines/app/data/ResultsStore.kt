package com.predictengines.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** Persists the user's ticks and crosses as one small JSON file in the app's private storage. */
class ResultsStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "prediction_marks.json")

    fun load(): Map<String, PredictionRecord> {
        if (!file.exists()) return emptyMap()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            val out = LinkedHashMap<String, PredictionRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val r = PredictionRecord(
                    id = o.getString("id"),
                    leagueId = o.getString("leagueId"),
                    leagueName = o.getString("leagueName"),
                    date = o.optString("date"),
                    home = o.getString("home"),
                    away = o.getString("away"),
                    source = o.getString("source"),
                    key = o.getString("key"),
                    label = o.getString("label"),
                    group = o.optString("group", "Other"),
                    correct = o.getBoolean("correct"),
                    markedAtMillis = o.optLong("markedAt"),
                    auto = o.optBoolean("auto", false)
                )
                out[r.id] = r
            }
            out
        } catch (e: JSONException) {
            emptyMap()
        }
    }

    fun save(records: Collection<PredictionRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id).put("leagueId", r.leagueId).put("leagueName", r.leagueName)
                    .put("date", r.date).put("home", r.home).put("away", r.away)
                    .put("source", r.source).put("key", r.key).put("label", r.label)
                    .put("group", r.group).put("correct", r.correct).put("markedAt", r.markedAtMillis).put("auto", r.auto)
            )
        }
        try {
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            // Best effort: a failed write just means the marks aren't kept after the app closes.
        }
    }
}
