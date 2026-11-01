package com.predictengines.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The key players you keep per team (key_players.json) and their photos (player_photos/<id>.jpg), plus the red cards
 * you already answered "who was sent off?" for, so the question is asked once.
 */
class PlayersStore(private val context: android.content.Context) {

    data class Saved(val players: List<KeyPlayer>, val answered: Set<String>)

    private val dir get() = context.applicationContext.filesDir
    private val file get() = File(dir, "key_players.json")
    private val photos get() = File(dir, "player_photos")

    fun load(): Saved = try {
        if (!file.exists()) Saved(emptyList(), emptySet()) else {
            val o = JSONObject(file.readText(Charsets.UTF_8))
            val arr = o.optJSONArray("players") ?: JSONArray()
            val players = (0 until arr.length()).mapNotNull { i ->
                val p = arr.getJSONObject(i)
                runCatching {
                    KeyPlayer(
                        id = p.getString("id"),
                        leagueId = p.getString("l"),
                        team = p.getString("t"),
                        name = p.getString("n"),
                        position = PlayerPosition.valueOf(p.optString("p", "MID")),
                        status = PlayerStatus.valueOf(p.optString("s", "AVAILABLE")),
                        returnDate = p.optString("r", ""),
                        banGames = p.optInt("bg", 0),
                        banFrom = p.optString("bf", ""),
                        photoStamp = p.optLong("ph", 0L)
                    )
                }.getOrNull()
            }
            val ans = o.optJSONArray("answered") ?: JSONArray()
            Saved(players, (0 until ans.length()).map { ans.getString(it) }.toSet())
        }
    } catch (e: Exception) {
        Saved(emptyList(), emptySet())
    }

    fun save(s: Saved) {
        try {
            val arr = JSONArray()
            for (p in s.players) {
                arr.put(
                    JSONObject().put("id", p.id).put("l", p.leagueId).put("t", p.team).put("n", p.name)
                        .put("p", p.position.name).put("s", p.status.name).put("r", p.returnDate)
                        .put("bg", p.banGames).put("bf", p.banFrom).put("ph", p.photoStamp)
                )
            }
            val o = JSONObject().put("players", arr).put("answered", JSONArray(s.answered.toList()))
            if (s.players.isEmpty() && s.answered.isEmpty()) file.delete() else file.writeText(o.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            // best effort
        }
    }

    /** Copies a picked picture into the app, scaled down to at most 320 px (a small square-ish JPEG). */
    fun savePhoto(id: String, resolver: ContentResolver, uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 640) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) false else {
            val scale = 320f / maxOf(bmp.width, bmp.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
            photos.mkdirs()
            File(photos, "$id.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        }
    } catch (e: Exception) {
        false
    }

    fun deletePhoto(id: String) {
        try { File(photos, "$id.jpg").delete() } catch (e: Exception) { /* best effort */ }
    }
}
