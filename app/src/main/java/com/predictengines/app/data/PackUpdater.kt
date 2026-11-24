package com.predictengines.app.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Priwin Picks: downloads the data pack (the zip Priwin makes with "Publish data pack") and installs it like a restore.
 * The pack is only installed when the whole download is a valid pack, so a broken or interrupted download changes nothing.
 * A host that sends an ETag or Last-Modified lets the app skip the download when nothing changed.
 */
class PackUpdater(context: Context, private val backup: BackupManager) {
    sealed class Result {
        object NoSource : Result()
        object Skipped : Result()
        object UpToDate : Result()
        data class Updated(val files: Int) : Result()
        data class Failed(val message: String) : Result()
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("pack_updater", Context.MODE_PRIVATE)

    /**
     * First launch: installs the starter pack bundled with the app (app/src/lite/assets/starter_pack.zip, a "Publish data pack" file),
     * so the app has picks before its first download. Returns true when something was installed.
     */
    fun installStarter(): Boolean {
        if (prefs.getBoolean("starter_done", false)) return false
        return try {
            val r = appContext.assets.open("starter_pack.zip").use { backup.restore(it) }
            if (r is BackupManager.Restored.Ok) { prefs.edit().putBoolean("starter_done", true).apply(); true } else false
        } catch (e: Exception) {
            false
        }
    }

    /** Blocking: call off the main thread. Without [force], a check made in the last 30 minutes is not repeated. */
    fun update(url: String, force: Boolean): Result {
        if (url.isBlank()) return Result.NoSource
        if (!url.startsWith("https://")) return Result.Failed("The data link must start with https://")
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checked", 0L) < CHECK_EVERY_MS) return Result.Skipped
        val tmp = File(appContext.cacheDir, "pack_download.zip")
        var conn: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "PriwinPicks")
            if (!force) {
                prefs.getString("etag", null)?.let { c.setRequestProperty("If-None-Match", it) }
                prefs.getString("modified", null)?.let { c.setRequestProperty("If-Modified-Since", it) }
            }
            val code = c.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                prefs.edit().putLong("checked", now).apply()
                return Result.UpToDate
            }
            if (code != HttpURLConnection.HTTP_OK) return Result.Failed("The data server answered $code. Showing the last data you have.")
            if (c.contentLengthLong > MAX_BYTES) return Result.Failed("The data pack is too big.")
            var total = 0L
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return Result.Failed("The data pack is too big.")
                        out.write(buf, 0, n)
                    }
                }
            }
            val restored = tmp.inputStream().use { backup.restore(it) }
            return when (restored) {
                is BackupManager.Restored.Ok -> {
                    prefs.edit()
                        .putLong("checked", now)
                        .putString("etag", c.getHeaderField("ETag"))
                        .putString("modified", c.getHeaderField("Last-Modified"))
                        .apply()
                    Result.Updated(restored.files)
                }
                is BackupManager.Restored.Failed -> Result.Failed(restored.message)
            }
        } catch (e: Exception) {
            return Result.Failed("Couldn't reach the data server. Showing the last data you have.")
        } finally {
            conn?.disconnect()
            tmp.delete()
        }
    }

    private companion object {
        const val CHECK_EVERY_MS = 30L * 60 * 1000
        const val MAX_BYTES = 200L * 1024 * 1024
    }
}
