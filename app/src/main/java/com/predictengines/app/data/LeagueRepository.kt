package com.predictengines.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.predictengines.app.engine.StatsUpdater
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.Locale

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
    fun leagues(): List<League> {
        val hidden = hiddenIds()
        return resolved().first.filter { it.id !in hidden }
    }

    /** Leagues you hid; their data and your records are kept, and you can show them again. */
    fun hiddenLeagues(): List<League> {
        val hidden = hiddenIds()
        return resolved().first.filter { it.id in hidden }
    }

    /** Leagues left out because another league has the same name and better files. Their data is not touched. */
    fun duplicateLeagues(): List<League> = resolved().second

    private fun nameKey(name: String) = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** True when a league with this name (ignoring case, spaces and punctuation) already exists. */
    fun nameTaken(name: String): Boolean {
        val k = nameKey(name)
        return (bundledLeagues + loadCustomLeagues()).any { nameKey(it.name) == k }
    }

    /**
     * Two leagues never share a name. When they do, the one with the right files is kept: matches results first,
     * then fixtures, then a built-in league over a custom one, then the one with more matches rows.
     * Returns (leagues to show, leagues left out).
     */
    private fun resolved(): Pair<List<League>, List<League>> {
        val all = bundledLeagues + loadCustomLeagues()
        val groups = all.groupBy { nameKey(it.name) }
        if (groups.values.all { it.size == 1 }) return all to emptyList()
        val keep = HashSet<String>()
        for ((_, group) in groups) {
            if (group.size == 1) { keep.add(group[0].id); continue }
            val best = group.maxByOrNull { l ->
                val rows = try { baseMatches(l).size } catch (e: Exception) { 0 }
                val fixtures = fileFor(l, DataKind.FIXTURES).exists() || builtInFixturesPath(l) != null
                (if (rows > 0) 4_000_000_000L else 0L) + (if (fixtures) 2_000_000_000L else 0L) +
                    (if (!l.isCustom) 1_000_000_000L else 0L) + rows.toLong()
            }!!
            keep.add(best.id)
        }
        return all.filter { it.id in keep } to all.filter { it.id !in keep }
    }

    private fun hiddenIds(): Set<String> =
        prefs.getString(KEY_HIDDEN, "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    fun hideLeague(league: League) {
        prefs.edit().putString(KEY_HIDDEN, (hiddenIds() + league.id).joinToString("\n")).apply()
    }

    fun showLeague(league: League) {
        prefs.edit().putString(KEY_HIDDEN, (hiddenIds() - league.id).joinToString("\n")).apply()
    }

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

    /**
     * Games played on or after this date (yyyy-MM-dd) are settled automatically when their score turns up in a
     * matches file. It starts as the day the feature is first used, so games already counted in a stats file
     * you loaded are never counted a second time.
     */
    fun autoSettleFrom(): String =
        prefs.getString(KEY_AUTO_SETTLE, null) ?: DEFAULT_AUTO_SETTLE_FROM.also {
            prefs.edit().putString(KEY_AUTO_SETTLE, it).apply()
        }

    /** The day the bundled stats file is current to, plus one: games before it are already counted in the file. */
    fun statsCurrentTo(): String =
        runCatching { java.time.LocalDate.parse(autoSettleFrom()).minusDays(1).toString() }.getOrDefault(DEFAULT_STATS_CURRENT_TO)

    fun setAutoSettleFrom(date: String) {
        prefs.edit().putString(KEY_AUTO_SETTLE, date).apply()
    }

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

    /** The league's matches file as parsed (imported file, else the built-in asset). Names are not unified yet. */
    private fun baseMatches(league: League): List<Match> {
        val imported = fileFor(league, DataKind.MATCHES)
        val asset = league.assetFile
        val text = when {
            imported.exists() -> imported.readText(Charsets.UTF_8)
            asset != null -> appContext.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
            else -> return emptyList()
        }
        return CsvParser.parseMatches(text).rows
    }

    /**
     * The league's results: the matches file plus the scores you entered by hand. Your own entry always wins for
     * a game that is in both (see [scoreConflicts] for when they disagree). Scores written in a fixtures file's
     * Result column are NOT used, because that data can be wrong.
     * One club, one name: "Man City" and "Manchester City" in the same file become one team.
     */
    fun loadMatches(league: League): List<Match> {
        val base = baseMatches(league)
        val added = loadAddedResults(league)
        val all = TeamNames.unifyMatches(base + added)
        val baseU = all.take(base.size)
        val addedU = all.drop(base.size)
        val mine = addedU.map { "${it.date}|${it.homeTeam}|${it.awayTeam}" }.toHashSet()
        val fileExtras = baseU.filter { it.extras != null }.associate { "${it.date}|${it.homeTeam}|${it.awayTeam}" to it.extras }
        return baseU.filter { "${it.date}|${it.homeTeam}|${it.awayTeam}" !in mine } +
            addedU.map { m -> fileExtras["${m.date}|${m.homeTeam}|${m.awayTeam}"]?.let { m.copy(extras = it) } ?: m }
    }

    /** Games you entered by hand whose score differs from the matches file and that you haven't ruled on yet. */
    fun scoreConflicts(league: League): List<ScoreConflict> {
        val added = loadAddedResults(league)
        if (added.isEmpty()) return emptyList()
        val base = baseMatches(league)
        if (base.isEmpty()) return emptyList()
        val all = TeamNames.unifyMatches(base + added)
        val csv = HashMap<String, Match>()
        for (m in all.take(base.size)) csv["${m.date}|${m.homeTeam}|${m.awayTeam}"] = m
        val out = ArrayList<ScoreConflict>()
        for (m in all.drop(base.size)) {
            val c = csv["${m.date}|${m.homeTeam}|${m.awayTeam}"] ?: continue
            if (c.homeGoals == m.homeGoals && c.awayGoals == m.awayGoals) continue
            val conflict = ScoreConflict(m.date, m.homeTeam, m.awayTeam, m.homeGoals, m.awayGoals, c.homeGoals, c.awayGoals)
            // "Keep mine" is remembered together with the file score it was decided against.
            if (prefs.getString(conflictKey(league, conflict), null) == "${c.homeGoals}-${c.awayGoals}") continue
            out.add(conflict)
        }
        return out
    }

    private fun conflictKey(league: League, c: ScoreConflict) = "conflict_${league.id}|${c.key}"

    /** You decided your own entry is right: stop asking until the file's score for that game changes again. */
    fun keepMyScore(league: League, c: ScoreConflict) {
        prefs.edit().putString(conflictKey(league, c), "${c.csvHome}-${c.csvAway}").apply()
    }

    /** You decided the matches file is right: your entry for that game is removed so the file's score is used. */
    fun useFileScore(league: League, c: ScoreConflict) {
        val added = loadAddedResults(league)
        val base = baseMatches(league)
        val unified = TeamNames.unifyMatches(base + added).drop(base.size)
        val kept = added.filterIndexed { i, _ ->
            val u = unified.getOrNull(i)
            u == null || "${u.date}|${u.homeTeam}|${u.awayTeam}" != c.key
        }
        writeAdded(league, kept)
        prefs.edit().remove(conflictKey(league, c)).apply()
    }

    /**
     * Changes whenever anything a league's loaded data is built from changes: its own files, the shared stats
     * files, the name links, the built-in asset file it uses, or the app itself being updated.
     */
    fun fingerprint(league: League): String {
        fun stamp(f: File) = if (f.exists()) "${f.length()}:${f.lastModified()}" else "-"
        val installed = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).lastUpdateTime
        } catch (e: Exception) {
            0L
        }
        val dir = File(root, league.id)
        return listOf(
            "v$CACHE_VERSION", installed.toString(), league.assetFile.orEmpty(),
            stamp(File(dir, "matches.csv")), stamp(File(dir, "fixtures.csv")), stamp(File(dir, "added_results.csv")),
            stamp(statsFile), stamp(deletedFile), stamp(ledgerFile),
            stamp(File(appContext.filesDir, "name_links.json"))
        ).joinToString("|")
    }

    // ---- scores added in the app -----------------------------------------------------------

    private fun addedFile(league: League) = File(File(root, league.id), "added_results.csv")

    /** Scores the user typed in for played fixtures (kept apart from the league's own matches file). */
    fun loadAddedResults(league: League): List<Match> {
        val f = addedFile(league)
        if (!f.exists()) return emptyList()
        return CsvParser.parseMatches(f.readText(Charsets.UTF_8)).rows
    }

    /** Adds or replaces the score of one game (same date + teams). */
    fun saveResult(league: League, match: Match) {
        val list = loadAddedResults(league).filterNot { sameGame(it, match) } + match
        writeAdded(league, list)
    }

    fun removeResult(league: League, match: Match) {
        writeAdded(league, loadAddedResults(league).filterNot { sameGame(it, match) })
    }

    private fun sameGame(a: Match, b: Match) = a.date == b.date && a.homeTeam == b.homeTeam && a.awayTeam == b.awayTeam

    private fun writeAdded(league: League, list: List<Match>) {
        val f = addedFile(league)
        if (list.isEmpty()) { f.delete(); return }
        f.parentFile?.mkdirs()
        val sb = StringBuilder("Date,HomeTeam,AwayTeam,FTHG,FTAG\n")
        for (m in list) sb.append(csvCell(m.date)).append(',').append(csvCell(m.homeTeam)).append(',')
            .append(csvCell(m.awayTeam)).append(',').append(m.homeGoals).append(',').append(m.awayGoals).append('\n')
        f.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun csvCell(s: String) =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** The league's matches (file + added scores) as CSV, for exporting an up-to-date file. */
    fun matchesCsv(league: League): String {
        val sb = StringBuilder("Date,HomeTeam,AwayTeam,FTHG,FTAG\n")
        for (m in loadMatches(league)) sb.append(csvCell(m.date)).append(',').append(csvCell(m.homeTeam)).append(',')
            .append(csvCell(m.awayTeam)).append(',').append(m.homeGoals).append(',').append(m.awayGoals).append('\n')
        return sb.toString()
    }

    fun statsCsv(): String = serializeStats(loadStats())

    /** Writes [text] to a file the user picked with the system "create document" dialog. */
    fun writeTo(uri: Uri, text: String): Boolean = try {
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (e: Exception) {
        false
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

    /**
     * Back to how the league came in the app: its imported matches and fixtures files, the scores you entered and
     * their decisions are removed, and what those scores added to the stats is taken back out.
     * (The stats file itself, other leagues and your own ticks are not touched.)
     */
    fun resetLeague(league: League) {
        clearImport(league, DataKind.MATCHES)
        clearImport(league, DataKind.FIXTURES)
        writeAdded(league, emptyList())
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("conflict_${league.id}|") }.forEach { e.remove(it) }
        e.apply()
        val ledger = loadLedger()
        val mine = ledger.filter { it.fx.startsWith("${league.id}|") }
        if (mine.isNotEmpty()) {
            val rows = loadStats()
            val undone = StatsUpdater.apply(rows, mine.map { StatDelta(it.rowKey, it.dv, it.dg, it.tag) }, sign = -1)
            if (undone.isNotEmpty()) saveUserRows(undone)
            saveLedger(ledger - mine.toSet())
        }
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
        val gone = deletedKeys()
        val merged = mergeStats(builtIn + loadImportedStats())
        return if (gone.isEmpty()) merged else merged.filter { StatsUpdater.rowKey(it) !in gone }
    }

    // Rows the user deleted stay deleted even though the built-in file still contains them.
    private val deletedFile: File get() = File(root, "stats_deleted.txt")

    private fun deletedKeys(): Set<String> =
        if (deletedFile.exists()) deletedFile.readLines().filter { it.isNotBlank() }.toSet() else emptySet()

    private fun saveDeletedKeys(keys: Set<String>) {
        if (keys.isEmpty()) { deletedFile.delete(); return }
        deletedFile.parentFile?.mkdirs()
        deletedFile.writeText(keys.sorted().joinToString("\n"), Charsets.UTF_8)
    }

    /** Saves full rows as the user's own copy (they win over the built-in rows) and un-deletes them. */
    private fun saveUserRows(changed: List<StatRow>, fileName: String = "Edits made in the app") {
        if (changed.isEmpty()) return
        val merged = mergeStats(loadImportedStats() + changed)
        statsFile.parentFile?.mkdirs()
        statsFile.writeText(serializeStats(merged), Charsets.UTF_8)
        val gone = deletedKeys() - changed.map { StatsUpdater.rowKey(it) }.toSet()
        saveDeletedKeys(gone)
        val keep = statsInfo()
        val info = ImportInfo(keep?.fileName ?: fileName, merged.size, keep?.skipped ?: 0, System.currentTimeMillis())
        prefs.edit().putString(KEY_STATS_INFO, toJson(info)).apply()
    }

    /** Changes the numbers of one row; the percentage is recalculated. */
    fun editStatRow(row: StatRow, value: Double, games: Int) {
        val g = games.coerceAtLeast(0)
        val v = value.coerceIn(0.0, g.toDouble())
        saveUserRows(listOf(row.copy(value = v, games = g, percent = StatsUpdater.percentOf(v, g) ?: row.percent)))
    }

    /** Adds a row (or replaces the same market + season + team). */
    fun addStatRow(row: StatRow) {
        val g = row.games.coerceAtLeast(0)
        val v = row.value.coerceIn(0.0, g.toDouble())
        saveUserRows(listOf(row.copy(value = v, games = g, percent = StatsUpdater.percentOf(v, g))))
    }

    fun deleteStatRows(rows: List<StatRow>) {
        if (rows.isEmpty()) return
        val keys = rows.map { StatsUpdater.rowKey(it) }.toSet()
        val mine = loadImportedStats().filter { StatsUpdater.rowKey(it) !in keys }
        if (statsFile.exists() || mine.isNotEmpty()) {
            statsFile.parentFile?.mkdirs()
            statsFile.writeText(serializeStats(mine), Charsets.UTF_8)
        }
        saveDeletedKeys(deletedKeys() + keys)
        // a deleted row can no longer be changed by earlier fixtures
        saveLedger(loadLedger().filter { it.rowKey !in keys })
    }

    // ---- stats ledger (what each fixture added to the stats) -------------------------------

    private val ledgerFile: File get() = File(root, "stats_ledger.json")

    fun loadLedger(): List<LedgerEntry> {
        if (!ledgerFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(ledgerFile.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LedgerEntry(o.getString("fx"), o.getString("row"), o.getInt("dv"), o.getInt("dg"), o.getString("tag"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLedger(list: List<LedgerEntry>) {
        if (list.isEmpty()) { ledgerFile.delete(); return }
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("fx", it.fx).put("row", it.rowKey).put("dv", it.dv).put("dg", it.dg).put("tag", it.tag))
        }
        ledgerFile.parentFile?.mkdirs()
        ledgerFile.writeText(arr.toString(), Charsets.UTF_8)
    }

    /**
     * Replaces what fixture [fx] contributed under every tag that matches [replaceTag]: the old changes are taken
     * back out of the stats, then [add] is applied. Returns how many stat rows now carry this fixture's changes.
     */
    fun updateStatsForFixture(fx: String, replaceTag: (String) -> Boolean, add: List<StatDelta>): Int {
        val ledger = loadLedger()
        val old = ledger.filter { it.fx == fx && replaceTag(it.tag) }
        val rows = loadStats()
        val undone = StatsUpdater.apply(
            rows, old.map { StatDelta(it.rowKey, it.dv, it.dg, it.tag) }, sign = -1
        )
        val afterUndo = if (undone.isEmpty()) rows else {
            val byKey = undone.associateBy { StatsUpdater.rowKey(it) }
            rows.map { byKey[StatsUpdater.rowKey(it)] ?: it }
        }
        val applied = StatsUpdater.apply(afterUndo, add)
        saveUserRows((undone + applied).associateBy { StatsUpdater.rowKey(it) }.values.toList())
        val kept = ledger.filterNot { it.fx == fx && replaceTag(it.tag) }
        saveLedger(kept + add.map { LedgerEntry(fx, it.rowKey, it.dv, it.dg, it.tag) })
        return add.size
    }

    /**
     * True when this game (same date, same two clubs, any league label) already has stats changes recorded
     * under a different fixture key, e.g. after a league or club name changed. Counting it again would double it.
     */
    fun countedUnderAnotherKey(fx: String): Boolean {
        val me = gameOf(fx) ?: return false
        return loadLedger().any { it.fx != fx && gameOf(it.fx) == me }
    }

    private fun gameOf(fx: String): String? {
        val p = fx.split('|')
        if (p.size < 4) return null
        return p[1] + "|" + listOf(p[2], p[3]).sorted().joinToString("|")
    }

    /**
     * Finds changes counted twice for one game (the same game under two keys, or the same change repeated), takes the
     * extra ones back out of the stats and returns how many were removed.
     */
    fun repairLedger(): Int {
        val ledger = loadLedger()
        val keepFx = HashMap<String, String>() // game -> the fixture key that stays
        val extras = ArrayList<LedgerEntry>()
        val seen = HashSet<String>()
        val kept = ArrayList<LedgerEntry>()
        for (e in ledger) {
            val g = gameOf(e.fx)
            if (g != null) {
                val owner = keepFx.getOrPut(g) { e.fx }
                if (owner != e.fx) { extras.add(e); continue }
            }
            if (!seen.add("${e.fx}|${e.rowKey}|${e.tag}")) { extras.add(e); continue }
            kept.add(e)
        }
        if (extras.isEmpty()) return 0
        val rows = loadStats()
        val undone = StatsUpdater.apply(rows, extras.map { StatDelta(it.rowKey, it.dv, it.dg, it.tag) }, sign = -1)
        if (undone.isNotEmpty()) saveUserRows(undone)
        saveLedger(kept)
        return extras.size
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
        saveDeletedKeys(deletedKeys() - TeamNames.unifyStats(parsed.rows).map { StatsUpdater.rowKey(it) }.toSet())

        try {
            statsFile.parentFile?.mkdirs()
            statsFile.writeText(serializeStats(merged), Charsets.UTF_8)
        } catch (e: Exception) {
            return ImportResult.Failure("Couldn't save the file: ${e.message}")
        }
        // Rows the new file supplies take its values; the app's earlier changes to those rows must not be applied
        // (or taken back) on top of them again.
        val supplied = TeamNames.unifyStats(parsed.rows).map { StatsUpdater.rowKey(it) }.toSet()
        val ledger = loadLedger()
        saveLedger(ledger.filter { it.rowKey !in supplied })
        val info = ImportInfo(displayName(uri), merged.size, parsed.skipped, System.currentTimeMillis())
        prefs.edit().putString(KEY_STATS_INFO, toJson(info)).apply()
        return ImportResult.Success(info)
    }

    fun clearStats() {
        statsFile.delete()
        deletedFile.delete()
        ledgerFile.delete()
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
        const val DEFAULT_STATS_CURRENT_TO = "2026-10-26"
        const val DEFAULT_AUTO_SETTLE_FROM = "2026-10-27"
        const val KEY_CUSTOM = "custom_leagues"
        const val KEY_HIDDEN = "hidden_leagues"
        const val KEY_LAST_LEAGUE = "last_league"
        const val KEY_AUTO_SETTLE = "auto_settle_from"
        const val KEY_STATS_INFO = "info_stats"
        const val BUILT_IN_STATS = "stats/statschecker_2026_2027.csv"
        const val CACHE_VERSION = 5
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }
}
