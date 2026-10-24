package com.predictengines.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

sealed class ImportResult {
    /** [note] says which columns were used / ignored, e.g. "used Date, Time ... · ignored 12 other columns". */
    data class Success(val info: ImportInfo, val note: String? = null) : ImportResult()
    data class Failure(val message: String) : ImportResult()
}

/**
 * Owns everything about where league data comes from:
 *  - built-in historical results (assets)
 *  - files the user imports from phone storage (copied into the app's private storage,
 *    so they keep working after the original file is moved or deleted)
 *  - user-created custom leagues
 */
class LeagueRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("predict_engines", Context.MODE_PRIVATE)
    private val root = File(appContext.filesDir, "league_data")

    // ---- leagues ---------------------------------------------------------------------------

    /** Built-in leagues are found by looking at the asset folders, so any number of them works. */
    fun leagues(): List<League> = bundledLeagues + loadCustomLeagues()

    private val bundledLeagues: List<League> by lazy {
        val found = LeagueCatalog.detect(
            matchFiles = listAssets("leagues"),
            fixtureFiles = listAssets("fixtures"),
            namesCsv = readAsset("leagues/${LeagueCatalog.NAMES_FILE}")
        )
        // A broken or empty assets folder must never leave the app without a league to open.
        found.ifEmpty { listOf(League("custom_default", "My league", null, true)) }
    }

    private fun listAssets(dir: String): List<String> = try {
        appContext.assets.list(dir)?.toList().orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    fun lastLeagueId(): String? = prefs.getString(KEY_LAST_LEAGUE, null)

    fun setLastLeague(id: String) {
        prefs.edit().putString(KEY_LAST_LEAGUE, id).apply()
    }

    fun addCustomLeague(name: String): League {
        val league = League(
            id = "custom_${System.currentTimeMillis()}",
            name = name.trim(),
            assetFile = null,
            isCustom = true
        )
        saveCustomLeagues(loadCustomLeagues() + league)
        return league
    }

    fun deleteCustomLeague(league: League) {
        if (!league.isCustom) return
        saveCustomLeagues(loadCustomLeagues().filter { it.id != league.id })
        File(root, league.id).deleteRecursively()
        prefs.edit()
            .remove(infoKey(league, DataKind.MATCHES))
            .remove(infoKey(league, DataKind.FIXTURES))
            .apply()
    }

    private fun loadCustomLeagues(): List<League> {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                League(o.getString("id"), o.getString("name"), null, true)
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun saveCustomLeagues(list: List<League>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }

    // ---- loading data ----------------------------------------------------------------------

    /** Imported matches file if there is one, otherwise the built-in asset (if the league has one). */
    fun loadMatches(league: League): List<Match> {
        val imported = fileFor(league, DataKind.MATCHES)
        val asset = league.assetFile
        val text = when {
            imported.exists() -> imported.readText(Charsets.UTF_8)
            asset != null -> appContext.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
            else -> return emptyList()
        }
        // One club, one name: "Man City" and "Manchester City" in the same file become one team.
        return TeamNames.unifyMatches(CsvParser.parseMatches(text).rows)
    }

    /** Imported fixtures file if there is one, otherwise the league's built-in fixtures (if any). */
    fun loadFixtures(league: League): List<Fixture> {
        val f = fileFor(league, DataKind.FIXTURES)
        val text = when {
            f.exists() -> f.readText(Charsets.UTF_8)
            else -> readAsset(builtInFixturesPath(league)) ?: return emptyList()
        }
        return CsvParser.parseFixtures(text).rows
    }

    fun hasBuiltInFixtures(league: League): Boolean = builtInFixturesPath(league) != null

    /** assets/fixtures/<league id>_fixtures.csv, for built-in leagues, when that file has been added. */
    private fun builtInFixturesPath(league: League): String? {
        if (league.isCustom) return null
        val name = "${league.id}${LeagueCatalog.FIXTURES_SUFFIX}"
        val present = try {
            appContext.assets.list("fixtures")?.contains(name) == true
        } catch (e: Exception) {
            false
        }
        return if (present) "fixtures/$name" else null
    }

    private fun readAsset(path: String?): String? {
        if (path == null) return null
        return try {
            appContext.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }.removePrefix("\uFEFF")
        } catch (e: Exception) {
            null
        }
    }

    // ---- importing from phone storage ------------------------------------------------------

    /** Reads a picked file as text (UTF-8, falling back to Latin-1), or returns an error message. */
    private fun readImportText(uri: Uri): Pair<String?, String?> {
        val bytes: ByteArray? = try {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
        if (bytes == null) return null to "Couldn't open that file."
        if (bytes.isEmpty()) return null to "That file is empty."
        if (bytes.size > MAX_FILE_BYTES) return null to "That file is too large (limit 20 MB)."
        var text = bytes.toString(Charsets.UTF_8)
        if (text.contains('\uFFFD')) text = bytes.toString(Charsets.ISO_8859_1)
        return text.removePrefix("\uFEFF") to null
    }

    fun importFile(league: League, kind: DataKind, uri: Uri): ImportResult {
        if (kind == DataKind.STATS) return importStats(uri)
        val (text, readError) = readImportText(uri)
        if (text == null) return ImportResult.Failure(readError ?: "Couldn't open that file.")

        val rows: Int
        val skipped: Int
        val error: String?
        val note: String?
        if (kind == DataKind.MATCHES) {
            val p = CsvParser.parseMatches(text)
            rows = p.rows.size; skipped = p.skipped; error = p.error; note = p.note
        } else {
            val p = CsvParser.parseFixtures(text)
            rows = p.rows.size; skipped = p.skipped; error = p.error; note = p.note
        }
        if (error != null || rows == 0) {
            return ImportResult.Failure(error ?: "No usable rows found in that file.")
        }

        val target = fileFor(league, kind)
        try {
            target.parentFile?.mkdirs()
            target.writeText(text, Charsets.UTF_8)
        } catch (e: Exception) {
            return ImportResult.Failure("Couldn't save the file: ${e.message}")
        }

        val info = ImportInfo(displayName(uri), rows, skipped, System.currentTimeMillis())
        prefs.edit().putString(infoKey(league, kind), toJson(info)).apply()
        return ImportResult.Success(info, note)
    }

    fun clearImport(league: League, kind: DataKind) {
        fileFor(league, kind).delete()
        prefs.edit().remove(infoKey(league, kind)).apply()
    }

    fun importInfo(league: League, kind: DataKind): ImportInfo? {
        if (!fileFor(league, kind).exists()) return null
        val raw = prefs.getString(infoKey(league, kind), null) ?: return null
        return try {
            val o = JSONObject(raw)
            ImportInfo(o.getString("name"), o.getInt("rows"), o.getInt("skipped"), o.getLong("at"))
        } catch (e: JSONException) {
            null
        }
    }

    // ---- stats file (global, not per league) -----------------------------------------------

    private val statsFile: File get() = File(root, "stats.csv")

    /** The built-in stats with anything the user loaded on top (same market + season + team: theirs wins). */
    fun loadStats(): List<StatRow> {
        val builtIn = readAsset(BUILT_IN_STATS)?.let { CsvParser.parseStats(it).rows }.orEmpty()
        return mergeStats(builtIn + loadImportedStats())
    }

    private fun loadImportedStats(): List<StatRow> {
        val f = statsFile
        if (!f.exists()) return emptyList()
        return CsvParser.parseStats(f.readText(Charsets.UTF_8)).rows
    }

    /** One name per club, then one row per market + season + team (the last one in the list wins). */
    private fun mergeStats(rows: List<StatRow>): List<StatRow> {
        val merged = LinkedHashMap<Triple<String, String, String>, StatRow>()
        for (r in TeamNames.unifyStats(rows)) {
            merged[Triple(r.market.lowercase().trim(), r.season.trim(), r.team.lowercase().trim())] = r
        }
        return merged.values.toList()
    }

    /**
     * Stats files hold teams from many leagues, so one shared store is used. Importing MERGES into
     * what is already loaded (built-in stats included) (same market + season + team is overwritten by the newer file), which
     * lets the user load one export per market. "Remove" wipes it.
     */
    fun importStats(uri: Uri): ImportResult {
        val (text, readError) = readImportText(uri)
        if (text == null) return ImportResult.Failure(readError ?: "Couldn't open that file.")
        val parsed = CsvParser.parseStats(text)
        if (parsed.error != null || parsed.rows.isEmpty()) {
            return ImportResult.Failure(parsed.error ?: "No usable rows found in that file.")
        }

        // Only the user's own rows are saved here; the built-in stats stay in the app and are merged on load.
        val merged = mergeStats(loadImportedStats() + parsed.rows)

        try {
            statsFile.parentFile?.mkdirs()
            statsFile.writeText(serializeStats(merged), Charsets.UTF_8)
        } catch (e: Exception) {
            return ImportResult.Failure("Couldn't save the file: ${e.message}")
        }
        val info = ImportInfo(displayName(uri), merged.size, parsed.skipped, System.currentTimeMillis())
        prefs.edit().putString(KEY_STATS_INFO, toJson(info)).apply()
        return ImportResult.Success(info)
    }

    fun clearStats() {
        statsFile.delete()
        prefs.edit().remove(KEY_STATS_INFO).apply()
    }

    fun statsInfo(): ImportInfo? {
        if (!statsFile.exists()) return null
        val raw = prefs.getString(KEY_STATS_INFO, null) ?: return null
        return try {
            val o = JSONObject(raw)
            ImportInfo(o.getString("name"), o.getInt("rows"), o.getInt("skipped"), o.getLong("at"))
        } catch (e: JSONException) {
            null
        }
    }

    private fun serializeStats(rows: Collection<StatRow>): String {
        fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        fun n(d: Double) = if (d == Math.rint(d)) d.toLong().toString() else d.toString()
        val sb = StringBuilder("market,season,rank,team,stat_value,games,percentage\n")
        for (r in rows) {
            sb.append(q(r.market)).append(',').append(q(r.season)).append(',')
                .append(r.rank?.toString().orEmpty()).append(',').append(q(r.team)).append(',')
                .append(n(r.value)).append(',').append(r.games).append(',')
                .append(r.percent?.let { n(it) + "%" }.orEmpty()).append('\n')
        }
        return sb.toString()
    }

    // ---- internals -------------------------------------------------------------------------

    private fun fileFor(league: League, kind: DataKind): File =
        File(File(root, league.id), if (kind == DataKind.MATCHES) "matches.csv" else "fixtures.csv")

    private fun infoKey(league: League, kind: DataKind) = "info_${league.id}_${kind.name}"

    private fun toJson(info: ImportInfo): String = JSONObject()
        .put("name", info.fileName)
        .put("rows", info.rows)
        .put("skipped", info.skipped)
        .put("at", info.importedAtMillis)
        .toString()

    private fun displayName(uri: Uri): String {
        try {
            appContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0) return c.getString(i)
                    }
                }
        } catch (e: Exception) {
            // fall through to a generic name
        }
        return uri.lastPathSegment ?: "imported.csv"
    }

    private companion object {
        const val KEY_CUSTOM = "custom_leagues"
        const val KEY_LAST_LEAGUE = "last_league"
        const val KEY_STATS_INFO = "info_stats"
        const val BUILT_IN_STATS = "stats/statschecker_2026_2027.csv"
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }
}
