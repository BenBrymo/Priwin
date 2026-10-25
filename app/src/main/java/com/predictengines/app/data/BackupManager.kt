package com.predictengines.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One-file backup of everything the user created in the app, so a reinstall (or a new phone) loses nothing:
 * ticks and crosses (the reports), linked team names, scores added to leagues, stats edits and deletions,
 * files loaded from the phone, and custom leagues. The built-in data and the speed cache are not included
 * (they come with the app / are rebuilt).
 */
class BackupManager(context: Context) {

    private val appContext = context.applicationContext
    private val files = appContext.filesDir
    private val prefs = appContext.getSharedPreferences("predict_engines", Context.MODE_PRIVATE)

    sealed class Restored {
        data class Ok(val files: Int) : Restored()
        data class Failed(val message: String) : Restored()
    }

    /** Writes the backup zip to [out]; returns how many files it holds. */
    fun write(out: OutputStream): Int {
        var count = 0
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put(MARKER, JSONObject().put("app", "Priwin").put("version", 1).put("at", System.currentTimeMillis()).toString().toByteArray())
            for (name in TOP_FILES) {
                val f = File(files, name)
                if (f.exists()) { put(name, f.readBytes()); count++ }
            }
            val league = File(files, LEAGUE_DIR)
            if (league.exists()) {
                league.walkTopDown().filter { it.isFile }.forEach { f ->
                    put(LEAGUE_DIR + "/" + f.relativeTo(league).path.replace(File.separatorChar, '/'), f.readBytes())
                    count++
                }
            }
            val p = JSONObject()
            for ((k, v) in prefs.all) if (v is String && (k == KEY_CUSTOM || k == KEY_LAST || k.startsWith("info_"))) p.put(k, v)
            put(PREFS_FILE, p.toString().toByteArray())
        }
        return count
    }

    /** Replaces the app's own data with the backup in [input]. Nothing is touched unless the whole file is valid. */
    fun restore(input: InputStream): Restored {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > MAX_BYTES) return Restored.Failed("That file is too big to be a Priwin backup.")
                    entries[e.name] = bytes
                }
            }
        } catch (ex: Exception) {
            return Restored.Failed("That doesn't look like a Priwin backup file.")
        }
        if (!entries.containsKey(MARKER)) return Restored.Failed("That doesn't look like a Priwin backup file.")
        val bad = entries.keys.firstOrNull { !allowed(it) }
        if (bad != null) return Restored.Failed("The backup contains an unexpected file ($bad), so it was not restored.")

        try {
            // wipe what the backup replaces, then lay the backup down
            for (name in TOP_FILES) File(files, name).delete()
            File(files, LEAGUE_DIR).deleteRecursively()
            File(files, "league_cache").deleteRecursively()
            var count = 0
            for ((name, bytes) in entries) {
                if (name == MARKER || name == PREFS_FILE) continue
                val target = File(files, name)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
                count++
            }
            entries[PREFS_FILE]?.let { raw ->
                val p = JSONObject(String(raw))
                val edit = prefs.edit()
                prefs.all.keys.filter { it == KEY_CUSTOM || it == KEY_LAST || it.startsWith("info_") }.forEach { edit.remove(it) }
                for (k in p.keys()) if (k == KEY_CUSTOM || k == KEY_LAST || k.startsWith("info_")) edit.putString(k, p.getString(k))
                edit.commit()
            }
            return Restored.Ok(count)
        } catch (ex: Exception) {
            return Restored.Failed("Couldn't restore the backup: ${ex.message}")
        }
    }

    /** Only files this app itself writes may be restored, and never outside its own folder. */
    private fun allowed(name: String): Boolean {
        if (name.contains("..") || name.startsWith("/") || name.contains('\\')) return false
        return name == MARKER || name == PREFS_FILE || name in TOP_FILES || (name.startsWith("$LEAGUE_DIR/") && name.length > LEAGUE_DIR.length + 1)
    }

    private companion object {
        const val MARKER = "priwin_backup.json"
        const val PREFS_FILE = "settings.json"
        const val LEAGUE_DIR = "league_data"
        val TOP_FILES = listOf("prediction_marks.json", "name_links.json")
        const val KEY_CUSTOM = "custom_leagues"
        const val KEY_LAST = "last_league"
        const val MAX_BYTES = 200L * 1024 * 1024
    }
}
