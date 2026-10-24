package com.predictengines.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.predictengines.app.data.Analysis
import com.predictengines.app.data.DataKind
import com.predictengines.app.data.DateUtils
import com.predictengines.app.data.Fixture
import com.predictengines.app.data.FixtureItem
import com.predictengines.app.data.FixtureSummary
import com.predictengines.app.data.ImportInfo
import com.predictengines.app.data.ImportResult
import com.predictengines.app.data.League
import com.predictengines.app.data.LeagueRepository
import com.predictengines.app.data.Match
import com.predictengines.app.data.NameLink
import com.predictengines.app.data.NameLinksStore
import com.predictengines.app.data.PredictionRecord
import com.predictengines.app.data.ResultsStore
import com.predictengines.app.data.TrackablePick
import com.predictengines.app.data.TopPick
import com.predictengines.app.data.StatDelta
import com.predictengines.app.data.LedgerEntry
import com.predictengines.app.data.fixtureKey
import com.predictengines.app.data.scoreKey
import com.predictengines.app.engine.StatsUpdater
import com.predictengines.app.engine.TopPicksEngine
import com.predictengines.app.data.StatRow
import com.predictengines.app.data.TeamMatcher
import com.predictengines.app.engine.BrymoEngine
import com.predictengines.app.engine.ConsensusEngine
import com.predictengines.app.engine.PoissonEngine
import com.predictengines.app.engine.StatsEngine
import com.predictengines.app.engine.TeamStatsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val leagues: List<League> = emptyList(),
    val selected: League? = null,
    val isLoading: Boolean = true,
    val teams: List<String> = emptyList(),
    val matchCount: Int = 0,
    val matchesImport: ImportInfo? = null,
    val fixturesImport: ImportInfo? = null,
    /** True when this league ships with its own fixtures file. */
    val builtInFixtures: Boolean = false,
    val fixtures: List<FixtureItem> = emptyList(),
    /** Known final scores of this league's games, keyed by [scoreKey] (matches file + scores added in the app). */
    val scores: Map<String, Match> = emptyMap(),
    /** The subset of [scores] the user typed in (these can be edited or removed). */
    val addedScoreKeys: Set<String> = emptySet(),
    /** Every row of the stats data in use (built-in + the user's edits), for the Stats editor. */
    val statsRows: List<StatRow> = emptyList(),

    // Stats model data (global: one stats file covers every league)
    val statsImport: ImportInfo? = null,
    val statsRowCount: Int = 0,
    val statsUsedMarkets: List<String> = emptyList(),
    val statsIgnoredMarkets: List<String> = emptyList(),

    /** Names the user linked as the same club (tap Link on an unrecognised team). */
    val nameLinks: List<NameLink> = emptyList(),
    /** Every team name in the stats data, for the link picker. */
    val statsTeams: List<String> = emptyList(),

    /** Best consensus picks across every league's upcoming fixtures (the Top picks tab). */
    val topPicks: List<TopPick> = emptyList(),
    val topPicksLoading: Boolean = true,

    /** The user's ticks and crosses, keyed by [PredictionRecord.id]. */
    val marks: Map<String, PredictionRecord> = emptyMap(),

    // Fixture detail
    val openFixtureId: Int? = null,
    val fixtureAnalysis: Analysis? = null,

    // Manual matchup
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val manualAnalysis: Analysis? = null,
    val manualError: String? = null,

    /** One-shot message for the snackbar. */
    val message: String? = null
)

private class LoadedData(
    val matches: List<Match>,
    val teams: List<String>,
    val fixtures: List<FixtureItem>,
    val matchesImport: ImportInfo?,
    val fixturesImport: ImportInfo?,
    val builtInFixtures: Boolean,
    val stats: List<StatRow>,
    val statsImport: ImportInfo?,
    val added: List<Match>
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LeagueRepository(application)
    private val results = ResultsStore(application)
    private val linksStore = NameLinksStore(application)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var matches: List<Match> = emptyList()
    private var stats: List<StatRow> = emptyList()
    private var ledger: List<LedgerEntry> = emptyList()
    private var loadJob: Job? = null
    private var topJob: Job? = null

    init {
        val leagues = repo.leagues()
        // The user's name links must be in place before any file is read, so every file sees the same names.
        val links = linksStore.load()
        TeamMatcher.setUserLinks(links.map { it.a to it.b })
        _state.update { it.copy(leagues = leagues, marks = results.load(), nameLinks = links) }
        val start = leagues.firstOrNull { it.id == repo.lastLeagueId() } ?: leagues.first()
        selectLeague(start.id)
    }

    // ---- league selection & loading --------------------------------------------------------

    fun selectLeague(id: String) {
        val league = _state.value.leagues.firstOrNull { it.id == id } ?: return
        repo.setLastLeague(league.id)
        _state.update {
            it.copy(
                selected = league,
                openFixtureId = null,
                fixtureAnalysis = null,
                manualAnalysis = null,
                manualError = null
            )
        }
        load(league, resetTeams = true)
    }

    private fun load(league: League, resetTeams: Boolean): Job {
        loadJob?.cancel()
        val job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val data = withContext(Dispatchers.IO) {
                val m = repo.loadMatches(league)
                val teams = m.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinct().sorted()
                val st = repo.loadStats()
                val led = repo.loadLedger()
                ledger = led
                val items = buildFixtureItems(repo.loadFixtures(league), m, teams, st, league.id, led)
                LoadedData(
                    m, teams, items,
                    repo.importInfo(league, DataKind.MATCHES),
                    repo.importInfo(league, DataKind.FIXTURES),
                    repo.hasBuiltInFixtures(league),
                    st,
                    repo.statsInfo(),
                    repo.loadAddedResults(league)
                )
            }
            matches = data.matches
            stats = data.stats
            val (usedMarkets, ignoredMarkets) = StatsEngine.marketSupport(data.stats)
            _state.update { s ->
                val keepHome = s.homeTeam?.takeIf { it in data.teams && !resetTeams }
                val keepAway = s.awayTeam?.takeIf { it in data.teams && !resetTeams }
                s.copy(
                    isLoading = false,
                    teams = data.teams,
                    matchCount = data.matches.size,
                    matchesImport = data.matchesImport,
                    fixturesImport = data.fixturesImport,
                    builtInFixtures = data.builtInFixtures,
                    statsImport = data.statsImport,
                    statsRowCount = data.stats.size,
                    statsUsedMarkets = usedMarkets,
                    statsIgnoredMarkets = ignoredMarkets,
                    fixtures = data.fixtures,
                    scores = data.matches.associateBy { m -> scoreKey(m.date, m.homeTeam, m.awayTeam) },
                    addedScoreKeys = data.added.map { m -> scoreKey(m.date, m.homeTeam, m.awayTeam) }.toSet(),
                    statsRows = data.stats,
                    homeTeam = keepHome ?: data.teams.getOrNull(0),
                    awayTeam = keepAway ?: data.teams.getOrNull(1),
                    openFixtureId = null,
                    fixtureAnalysis = null,
                    manualAnalysis = null,
                    statsTeams = data.stats.map { it.team }.distinct().sorted()
                )
            }
            refreshTopPicks()
        }
        loadJob = job
        return job
    }

    private fun matchesBefore(all: List<Match>, date: String): List<Match> =
        if (date.isBlank()) all else all.filter { it.date.isBlank() || it.date < date }

    private fun statsAsOf(
        leagueId: String, date: String, home: String, away: String, rows: List<StatRow>, led: List<LedgerEntry>
    ): List<StatRow> {
        if (led.isEmpty()) return rows
        val fx = fixtureKey(leagueId, date, home, away)
        val mine = led.filter { it.fx == fx }
        if (mine.isEmpty()) return rows
        val undone = StatsUpdater.apply(rows, mine.map { StatDelta(it.rowKey, it.dv, it.dg, it.tag) }, sign = -1)
        val byKey = undone.associateBy { StatsUpdater.rowKey(it) }
        return rows.map { byKey[StatsUpdater.rowKey(it)] ?: it }
    }

    /** Re-scores every league's fixtures in the background and keeps the 20 strongest picks. */
    private fun refreshTopPicks() {
        topJob?.cancel()
        topJob = viewModelScope.launch {
            _state.update { it.copy(topPicksLoading = true) }
            val leagues = _state.value.leagues
            val top = withContext(Dispatchers.Default) {
                val st = repo.loadStats()
                val all = ArrayList<TopPick>()
                for (league in leagues) {
                    try {
                        val m = repo.loadMatches(league)
                        val fixtures = repo.loadFixtures(league)
                        if (m.isEmpty() || fixtures.isEmpty()) continue
                        val teams = m.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinct().sorted()
                        all += TopPicksEngine.candidates(league, buildFixtureItems(fixtures, m, teams, st))
                    } catch (e: Exception) {
                        // one unreadable league must not hide the others
                    }
                }
                TopPicksEngine.rank(all)
            }
            _state.update { it.copy(topPicks = top, topPicksLoading = false) }
        }
    }

    /** Opens a Top-picks entry: switches to its league if needed, then opens that fixture's analysis. */
    fun openTopPick(pick: TopPick) {
        viewModelScope.launch {
            val current = _state.value.selected
            if (current?.id != pick.leagueId) {
                val league = _state.value.leagues.firstOrNull { it.id == pick.leagueId } ?: return@launch
                repo.setLastLeague(league.id)
                _state.update { it.copy(selected = league, openFixtureId = null, fixtureAnalysis = null, manualAnalysis = null, manualError = null) }
                load(league, resetTeams = true).join()
            }
            val item = _state.value.fixtures.firstOrNull {
                it.fixture.date == pick.date && it.home == pick.home && it.away == pick.away
            } ?: return@launch
            openFixture(item.id)
        }
    }

    private fun buildFixtureItems(
        fixtures: List<Fixture>,
        matches: List<Match>,
        teams: List<String>,
        statRows: List<StatRow>,
        leagueId: String = "",
        led: List<LedgerEntry> = emptyList()
    ): List<FixtureItem> {
        val sorted = fixtures.sortedBy { DateUtils.sortKey(it.date, it.time) }
        return sorted.mapIndexed { index, f ->
            val home = TeamMatcher.resolve(f.homeTeam, teams)
            val away = TeamMatcher.resolve(f.awayTeam, teams)
            val summary = if (home != null && away != null && home != away) {
                // A fixture is always predicted from what was known BEFORE it: earlier games only, and the
                // stats without the changes this very game made to them (once its score is in).
                val before = matchesBefore(matches, f.date)
                val st = statsAsOf(leagueId, f.date, home, away, statRows, led)
                FixtureSummary(
                    ConsensusEngine.compute(
                        home, away,
                        BrymoEngine.predict(home, away, before),
                        PoissonEngine.predict(home, away, before),
                        StatsEngine.predict(home, away, st)
                    )
                )
            } else null
            FixtureItem(index, f, home, away, summary)
        }
    }

    // ---- importing -------------------------------------------------------------------------

    fun importFile(kind: DataKind, uri: Uri) {
        val league = _state.value.selected ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repo.importFile(league, kind, uri) }
            when (result) {
                is ImportResult.Success -> {
                    val what = when (kind) {
                        DataKind.MATCHES -> "matches"
                        DataKind.FIXTURES -> "fixtures"
                        DataKind.STATS -> "stat rows (merged)"
                    }
                    val skipped = if (result.info.skipped > 0) " (${result.info.skipped} rows skipped)" else ""
                    val columns = result.note?.let { " · $it" }.orEmpty()
                    postMessage("Loaded ${result.info.rows} $what from ${result.info.fileName}$skipped$columns")
                    load(league, resetTeams = kind == DataKind.MATCHES)
                }
                is ImportResult.Failure -> postMessage(result.message)
            }
        }
    }

    fun clearImport(kind: DataKind) {
        val league = _state.value.selected ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (kind == DataKind.STATS) repo.clearStats() else repo.clearImport(league, kind)
            }
            postMessage(
                when (kind) {
                    DataKind.MATCHES ->
                        if (league.assetFile != null) "Restored the built-in match results" else "Match results removed"
                    DataKind.FIXTURES ->
                        if (_state.value.builtInFixtures) "Restored the built-in fixtures" else "Fixtures removed"
                    DataKind.STATS -> "Back to the built-in stats"
                }
            )
            load(league, resetTeams = kind == DataKind.MATCHES)
        }
    }

    // ---- custom leagues --------------------------------------------------------------------

    fun addLeague(name: String) {
        if (name.isBlank()) return
        val league = repo.addCustomLeague(name)
        _state.update { it.copy(leagues = repo.leagues()) }
        selectLeague(league.id)
        postMessage("League added. Now load its match results file.")
    }

    fun deleteLeague(league: League) {
        if (!league.isCustom) return
        repo.deleteCustomLeague(league)
        val leagues = repo.leagues()
        _state.update { it.copy(leagues = leagues) }
        if (_state.value.selected?.id == league.id) selectLeague(leagues.first().id)
        postMessage("Deleted ${league.name}")
    }

    // ---- manual matchup --------------------------------------------------------------------

    fun selectHome(team: String) = _state.update { it.copy(homeTeam = team, manualAnalysis = null) }
    fun selectAway(team: String) = _state.update { it.copy(awayTeam = team, manualAnalysis = null) }
    fun swapTeams() = _state.update {
        it.copy(homeTeam = it.awayTeam, awayTeam = it.homeTeam, manualAnalysis = null)
    }

    fun runManual() {
        val home = _state.value.homeTeam
        val away = _state.value.awayTeam
        if (home == null || away == null) {
            _state.update { it.copy(manualError = "Pick both a home and an away team.") }
            return
        }
        if (home == away) {
            _state.update { it.copy(manualError = "Home and away teams must be different.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(manualError = null) }
            val analysis = withContext(Dispatchers.Default) { analyze(home, away, null) }
            _state.update { it.copy(manualAnalysis = analysis) }
        }
    }

    // ---- fixture detail --------------------------------------------------------------------

    fun openFixture(id: Int) {
        val item = _state.value.fixtures.firstOrNull { it.id == id } ?: return
        val home = item.home
        val away = item.away
        if (home == null || away == null || home == away) return
        _state.update { it.copy(openFixtureId = id, fixtureAnalysis = null) }
        viewModelScope.launch {
            val kickoff = DateUtils.kickoffLabel(item.fixture.date, item.fixture.time).ifBlank { null }
            val analysis = withContext(Dispatchers.Default) { analyze(home, away, kickoff, item.fixture.date, true) }
            _state.update { if (it.openFixtureId == id) it.copy(fixtureAnalysis = analysis) else it }
        }
    }

    fun closeFixture() = _state.update { it.copy(openFixtureId = null, fixtureAnalysis = null) }

    // ---- misc ------------------------------------------------------------------------------

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun postMessage(text: String) = _state.update { it.copy(message = text) }

    // ---- name links ------------------------------------------------------------------------

    /** Tells the app that [name] and [target] are the same club, saves it, and re-reads the data. */
    fun linkNames(name: String, target: String) {
        val league = _state.value.selected ?: return
        if (name.isBlank() || target.isBlank() || name == target) return
        val current = _state.value.nameLinks
        if (current.any { (it.a == name && it.b == target) || (it.a == target && it.b == name) }) return
        applyLinks(current + NameLink(name, target))
        postMessage("Linked \"$name\" to \"$target\"")
        reloadKeepingView(league)
    }

    fun unlinkNames(link: NameLink) {
        val league = _state.value.selected ?: return
        applyLinks(_state.value.nameLinks.filter { it != link })
        postMessage("Removed the link between \"${link.a}\" and \"${link.b}\"")
        reloadKeepingView(league)
    }

    private fun applyLinks(links: List<NameLink>) {
        TeamMatcher.setUserLinks(links.map { it.a to it.b })
        _state.update { it.copy(nameLinks = links) }
        viewModelScope.launch(Dispatchers.IO) { linksStore.save(links) }
    }

    /** Reloads the data, then re-opens the fixture / matchup the user was looking at. */
    private fun reloadKeepingView(league: League) {
        val reopen = _state.value.openFixtureId
        val hadManual = _state.value.manualAnalysis != null
        viewModelScope.launch {
            load(league, resetTeams = false).join()
            if (reopen != null) openFixture(reopen)
            if (hadManual) runManual()
        }
    }

    // ---- ticks and crosses -----------------------------------------------------------------

    /** Marks a prediction correct (true), wrong (false) or clears it (null), and saves it on the phone. */
    fun setMark(date: String, home: String, away: String, pick: TrackablePick, correct: Boolean?) {
        val league = _state.value.selected ?: return
        val id = PredictionRecord.idOf(league.id, date, home, away, pick.source, pick.key)
        val updated = _state.value.marks.toMutableMap()
        if (correct == null) {
            updated.remove(id)
        } else {
            updated[id] = PredictionRecord(
                id, league.id, league.name, date, home, away,
                pick.source, pick.key, pick.label, pick.group, correct, System.currentTimeMillis()
            )
        }
        _state.update { it.copy(marks = updated) }
        viewModelScope.launch(Dispatchers.IO) { results.save(updated.values) }
        if (pick.source == "Stats") updateStatsFromTick(league, date, home, away, pick, correct)
    }

    // ---- keeping matches and stats current -------------------------------------------------

    /**
     * Ticking a Stats pick for corners, cards or first-to-score feeds that result into the stats data
     * (a score can't say). Picks the score decides are updated when the score is entered instead.
     */
    private fun updateStatsFromTick(league: League, date: String, home: String, away: String, pick: TrackablePick, correct: Boolean?) {
        if (StatsUpdater.isScoreDecided(pick.key)) {
            if (correct != null && !_state.value.scores.containsKey(scoreKey(date, home, away))) {
                postMessage("Add this game's score to update the Stats data")
            }
            return
        }
        viewModelScope.launch {
            val fx = fixtureKey(league.id, date, home, away)
            val tag = "tick:${pick.key}"
            val score = _state.value.scores[scoreKey(date, home, away)]
            val changed = withContext(Dispatchers.IO) {
                val deltas = if (correct == null) emptyList()
                else StatsUpdater.tickDeltas(pick.key, correct, home, away, score, repo.loadStats())
                repo.updateStatsForFixture(fx, { it == tag }, deltas)
            }
            if (correct != null) postMessage(if (changed > 0) "Stats data updated ($changed rows)" else "No stats rows to update for this pick")
            reloadKeepingView(league)
        }
    }

    /** Saves a fixture's final score into this league's matches and updates every stat the score settles. */
    fun saveScore(fixtureId: Int, homeGoals: Int, awayGoals: Int) {
        val league = _state.value.selected ?: return
        val item = _state.value.fixtures.firstOrNull { it.id == fixtureId } ?: return
        val home = item.home ?: return
        val away = item.away ?: return
        val date = item.fixture.date
        if (date.isBlank()) { postMessage("This fixture has no date, so its score can't be saved"); return }
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) {
                repo.saveResult(league, Match(date, home, away, homeGoals, awayGoals))
                val deltas = StatsUpdater.scoreDeltas(home, away, homeGoals, awayGoals, repo.loadStats())
                repo.updateStatsForFixture(fixtureKey(league.id, date, home, away), { it == "score" }, deltas)
            }
            postMessage("Saved $home $homeGoals-$awayGoals $away · $changed stats rows updated")
            reloadKeepingView(league)
        }
    }

    fun removeScore(fixtureId: Int) {
        val league = _state.value.selected ?: return
        val item = _state.value.fixtures.firstOrNull { it.id == fixtureId } ?: return
        val home = item.home ?: return
        val away = item.away ?: return
        val date = item.fixture.date
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.removeResult(league, Match(date, home, away, 0, 0))
                repo.updateStatsForFixture(fixtureKey(league.id, date, home, away), { it == "score" }, emptyList())
            }
            postMessage("Score removed; its stats changes were taken back")
            reloadKeepingView(league)
        }
    }

    // ---- stats editor ----------------------------------------------------------------------

    fun editStat(row: StatRow, value: Double, games: Int) = statsChange("Updated ${row.team} · ${row.market}") { repo.editStatRow(row, value, games) }

    fun addStat(row: StatRow) = statsChange("Added ${row.team} · ${row.market}") { repo.addStatRow(row) }

    fun deleteStats(rows: List<StatRow>) =
        statsChange(if (rows.size == 1) "Deleted ${rows[0].team} · ${rows[0].market}" else "Deleted ${rows.size} records") { repo.deleteStatRows(rows) }

    private fun statsChange(message: String, block: () -> Unit) {
        val league = _state.value.selected ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            postMessage(message)
            reloadKeepingView(league)
        }
    }

    fun exportStats(uri: Uri) = export(uri) { repo.statsCsv() }

    fun exportMatches(uri: Uri) {
        val league = _state.value.selected ?: return
        export(uri) { repo.matchesCsv(league) }
    }

    private fun export(uri: Uri, text: () -> String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.writeTo(uri, text()) }
            postMessage(if (ok) "File saved" else "Couldn't save the file")
        }
    }

    private fun analyze(
        home: String,
        away: String,
        kickoff: String?,
        date: String = "",
        forFixture: Boolean = false
    ): Analysis {
        val m = if (forFixture) matchesBefore(matches, date) else matches
        val league = _state.value.selected?.id.orEmpty()
        val statRows = if (forFixture) statsAsOf(league, date, home, away, stats, ledger) else stats
        val brymo = BrymoEngine.predict(home, away, m)
        val poisson = PoissonEngine.predict(home, away, m)
        val statsResult = StatsEngine.predict(home, away, statRows)
        val consensus = ConsensusEngine.compute(home, away, brymo, poisson, statsResult, DETAIL_MAX_PICKS)
        return Analysis(
            homeTeam = home,
            awayTeam = away,
            kickoff = kickoff,
            brymo = brymo,
            poisson = poisson,
            homeStats = TeamStatsEngine.stats(home, m),
            awayStats = TeamStatsEngine.stats(away, m),
            stats = statsResult,
            consensus = consensus,
            date = date,
            trackable = if (forFixture) {
                ConsensusEngine.trackable(home, away, brymo, poisson, statsResult, consensus)
            } else emptyList()
        )
    }

    private companion object {
        const val DETAIL_MAX_PICKS = 5
    }
}
