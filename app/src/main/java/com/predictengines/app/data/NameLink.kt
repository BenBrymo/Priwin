package com.predictengines.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * "These two names are the same club", created by the user inside the app (tap Link on an unrecognised team).
 * Works across every file: a link between a fixture name and a matches-file name, or between a matches-file
 * name and a stats-file name, makes the models treat them as one team.
 */
data class NameLink(val a: String, val b: String)

/** Keeps the user's links as a small JSON file in the app's private storage. */
class NameLinksStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "name_links.json")

    fun load(): List<NameLink> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                NameLink(o.getString("a"), o.getString("b"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun save(links: List<NameLink>) {
        val arr = JSONArray()
        links.forEach { arr.put(JSONObject().put("a", it.a).put("b", it.b)) }
        try {
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            // Best effort: if this fails the links just don't survive closing the app.
        }
    }
}
