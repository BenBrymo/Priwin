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
import com.predictengines.app.data.MatchExtras
import com.predictengines.app.data.ScoreCell
import com.predictengines.app.data.ScoreConflict
import com.predictengines.app.data.ListPicksStore
import com.predictengines.app.data.ResultExtrasStore
import com.predictengines.app.data.settleSignature
import com.predictengines.app.engine.ResultJudge
import com.predictengines.app.data.StatDelta
import com.predictengines.app.data.LedgerEntry
import com.predictengines.app.data.LeagueDiskCache
import com.predictengines.app.data.BackupManager
import com.predictengines.app.data.LoadedData
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
    /** Brymo's best handicap picks and the best "Over 1.5 goals" picks, best 20 per week across all leagues. */
    /** Played games with no score, per league id (from every league's loaded data). */
    val missingByLeague: Map<String, Int> = emptyMap(),
    val handicapPicks: List<TopPick> = emptyList(),
    val over15Picks: List<TopPick> = emptyList(),

    /** Corners / cards / first scorer the user typed in for played games, keyed by fixture. */
    val extras: Map<String, MatchExtras> = emptyMap(),
    /** Played games from this date on are settled automatically when their score appears in a matches file. */
    val autoSettleFrom: String = "",
    /** Hand-entered scores that differ from the matches file and still need your decision. */
    val conflicts: List<ScoreConflict> = emptyList(),

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

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LeagueRepository(application)
    private val disk = LeagueDiskCache(application)
    private val backup = BackupManager(application)
    private val extrasStore = ResultExtrasStore(application)
    private val listPicks = ListPicksStore(application)
    private val settling = java.util.concurrent.atomic.AtomicBoolean(false)
    private val results = ResultsStore(application)
    private val linksStore = NameLinksStore(application)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var matches: List<Match> = emptyList()
    private var stats: List<StatRow> = emptyList()
    private var ledger: List<LedgerEntry> = emptyList()
    private var loadJob: Job? = null
    private var topJob: Job? = null

    // Everything a league needs, kept for the whole session so going back to a league is instant.
    // Any change to data (an import, a score, a stats edit, a name link) clears it, because stats and
    // name links are shared by every league. [cacheGen] stops a slow background load that started
    // before a change from putting its out-of-date result back.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, LoadedData>()
    private val cacheGen = java.util.concurrent.atomic.AtomicInteger(0)
    private var topCache: List<TopPick>? = null
    private var handicapCache: List<TopPick> = emptyList()
    private var over15Cache: List<TopPick> = emptyList()
    private var missingCache: Map<String, Int> = emptyMap()

    private fun invalidateCache() {
        cacheGen.incrementAndGet()
        cache.clear()
        topCache = null
        topJob?.cancel()
    }

    /** Reads and scores one league (slow: parses files and predicts every fixture). Cached by [load]. */
    private fun computeLeague(league: League): LoadedData {
        val gen = cacheGen.get()
        // Same inputs as last time (nothing edited, app not updated)? Reuse what was worked out then.
        val fingerprint = repo.fingerprint(league)
        disk.load(league.id, fingerprint)?.let { saved ->
            if (gen == cacheGen.get()) cache[league.id] = saved
            return saved
        }
        val m = repo.loadMatches(league)
        val teams = m.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinct().sorted()
        val st = repo.loadStats()
        val led = repo.loadLedger()
        val items = buildFixtureItems(repo.loadFixtures(league), m, teams, st, league.id, led)
        val data = LoadedData(
            m, teams, items,
            repo.importInfo(league, DataKind.MATCHES),
            repo.importInfo(league, DataKind.FIXTURES),
            repo.hasBuiltInFixtures(league),
            st,
            repo.statsInfo(),
            repo.loadAddedResults(league),
            led
        )
        if (gen == cacheGen.get()) {
            cache[league.id] = data
            disk.save(league.id, fingerprint, data)
        }
        return data
    }

    init {
        val leagues = repo.leagues()
        // The user's name links must be in place before any file is read, so every file sees the same names.
        val links = linksStore.load()
        TeamMatcher.setUserLinks(links.map { it.a to it.b })
        _state.update { it.copy(leagues = leagues, marks = results.load(), nameLinks = links, extras = extrasStore.loadExtras(), autoSettleFrom = repo.autoSettleFrom()) }
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

    private fun load(league: League, resetTeams: Boolean, fresh: Boolean = false): Job {
        loadJob?.cancel()
        if (fresh) invalidateCache()
        val job = viewModelScope.launch {
            val cached = cache[league.id]
            if (cached == null) _state.update { it.copy(isLoading = true) }
            val data = cached ?: withContext(Dispatchers.IO) {
                computeLeague(league)
            }
            val conflicts = withContext(Dispatchers.IO) { repo.scoreConflicts(league) }
            matches = data.matches
            stats = data.stats
            ledger = data.ledger
            val (usedMarkets, ignoredMarkets) = StatsEngine.marketSupport(data.stats)
            _state.update { s ->
                val keepHome = s.homeTeam?.takeIf { it in data.teams && !resetTeams }
                val keepAway = s.awayTeam?.takeIf { it in data.teams && !resetTeams }
                s.copy(
                    isLoading = false,
                    conflicts = conflicts,
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
            autoSettleLoaded(league)
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
        topCache?.let { cached ->
            // games that kicked off since the list was built drop out
            val live = cached.filter { !DateUtils.isPlayed(it.date, it.time) }
            _state.update {
                it.copy(
                    topPicks = live, topPicksLoading = false,
                    handicapPicks = handicapCache.filter { p -> !DateUtils.isPlayed(p.date, p.time) },
                    over15Picks = over15Cache.filter { p -> !DateUtils.isPlayed(p.date, p.time) },
                    missingByLeague = missingCache
                )
            }
            return
        }
        topJob?.cancel()
        topJob = viewModelScope.launch {
            _state.update { it.copy(topPicksLoading = true) }
            val leagues = _state.value.leagues
            val gen = cacheGen.get()
            val top = withContext(Dispatchers.Default) {
                val all = ArrayList<TopPick>()
                val hcp = ArrayList<TopPick>()
                val o15 = ArrayList<TopPick>()
                val missing = HashMap<String, Int>()
                for (league in leagues) {
                    try {
                        // Uses (and fills) the same per-league cache, so the other leagues are ready before you open them.
                        val data = cache[league.id] ?: computeLeague(league)
                        if (data.fixtures.isEmpty()) continue
                        missing[league.id] = countMissing(data)
                        if (data.matches.isEmpty()) continue
                        all += TopPicksEngine.candidates(league, data.fixtures)
                        hcp += TopPicksEngine.handicapCandidates(league, data.fixtures)
                        o15 += TopPicksEngine.over15Candidates(league, data.fixtures)
                    } catch (e: Exception) {
                        // one unreadable league must not hide the others
                    }
                }
                missingCache = missing
                Triple(TopPicksEngine.rank(all), TopPicksEngine.rank(hcp), TopPicksEngine.rank(o15))
            }
            withContext(Dispatchers.IO) { recordListPicks(top.first, top.second, top.third) }
            if (gen == cacheGen.get()) {
                topCache = top.first; handicapCache = top.second; over15Cache = top.third
            }
            _state.update {
                it.copy(
                    topPicks = top.first, handicapPicks = top.second, over15Picks = top.third,
                    missingByLeague = missingCache, topPicksLoading = false
                )
            }
        }
    }

    /** Played games of a league that have no score in the matches file or among your entries. */
    private fun countMissing(data: LoadedData): Int {
        val now = java.time.LocalDateTime.now()
        val known = data.matches.mapTo(HashSet()) { scoreKey(it.date, it.homeTeam, it.awayTeam) }
        return data.fixtures.count { fx ->
            val h = fx.home; val a = fx.away
            h != null && a != null && fx.fixture.date.isNotBlank() &&
                DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now) &&
                scoreKey(fx.fixture.date, h, a) !in known
        }
    }

    /** Writes down the picks on the three weekly lists, so they can be settled and reported after their games. */
    private fun recordListPicks(top: List<TopPick>, hcp: List<TopPick>, o15: List<TopPick>) {
        fun entries(source: String, list: List<TopPick>) = list.map { tp ->
            ListPicksStore.Entry(
                source, tp.leagueId, tp.leagueName, tp.date, tp.home, tp.away,
                tp.pick.key, tp.pick.label, tp.pick.group
            )
        }
        listPicks.add(entries(ListPicksStore.TOP, top) + entries(ListPicksStore.HANDICAP, hcp) + entries(ListPicksStore.OVER15, o15))
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
                val brymo = BrymoEngine.predict(home, away, before)
                val poisson = PoissonEngine.predict(home, away, before)
                val stats = StatsEngine.predict(home, away, st)
                FixtureSummary(
                    ConsensusEngine.compute(home, away, brymo, poisson, stats),
                    ConsensusEngine.handicapPick(home, away, brymo),
                    ConsensusEngine.over15Backers(home, away, poisson, stats)
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
                    load(league, resetTeams = kind == DataKind.MATCHES, fresh = true)
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
            load(league, resetTeams = kind == DataKind.MATCHES, fresh = true)
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
        invalidateCache()
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
            load(league, resetTeams = false, fresh = true).join()
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

    // ---- scores, settling and auto-ticks ---------------------------------------------------

    /** One score typed in for a fixture, with the optional extras. */
    data class ScoreEntry(val fixtureId: Int, val homeGoals: Int, val awayGoals: Int, val extras: MatchExtras? = null)

    fun saveScore(fixtureId: Int, homeGoals: Int, awayGoals: Int, extras: MatchExtras? = null) =
        saveScores(listOf(ScoreEntry(fixtureId, homeGoals, awayGoals, extras)))

    /**
     * Saves scores into this league's matches, then settles each game: every stat the score decides is updated,
     * and every pick the score (and extras) decide is ticked or crossed. Picks you ticked yourself are left alone.
     */
    fun saveScores(entries: List<ScoreEntry>) {
        val league = _state.value.selected ?: return
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val marks = _state.value.marks.toMutableMap()
                val extrasMap = _state.value.extras.toMutableMap()
                val settled = extrasStore.loadSettled().toMutableMap()
                var rows = 0
                var games = 0
                for (e in entries) {
                    val item = _state.value.fixtures.firstOrNull { it.id == e.fixtureId } ?: continue
                    val home = item.home ?: continue
                    val away = item.away ?: continue
                    val date = item.fixture.date
                    if (date.isBlank()) continue
                    val fx = fixtureKey(league.id, date, home, away)
                    repo.saveResult(league, Match(date, home, away, e.homeGoals, e.awayGoals))
                    // You just chose this score on purpose, so don't ask about the file's different score for it.
                    repo.scoreConflicts(league).filter { it.key == "$date|$home|$away" }.forEach { repo.keepMyScore(league, it) }
                    if (e.extras == null || e.extras.isEmpty) extrasMap.remove(fx) else extrasMap[fx] = e.extras
                    rows += settleOne(league, date, home, away, e.homeGoals, e.awayGoals, extrasMap[fx], marks)
                    settled[fx] = settleSignature(e.homeGoals, e.awayGoals, extrasMap[fx])
                    games++
                }
                results.save(marks.values)
                extrasStore.saveExtras(extrasMap)
                extrasStore.saveSettled(settled)
                Triple(games, rows, Pair(marks.toMap(), extrasMap.toMap()))
            }
            val (games, rows, state) = result
            _state.update { it.copy(marks = state.first, extras = state.second) }
            postMessage(
                if (games == 0) "Nothing to save"
                else "Saved $games ${if (games == 1) "score" else "scores"} · picks settled · $rows stats rows updated"
            )
            reloadKeepingView(league)
        }
    }

    /**
     * Settles one game: stats from the score, then each pick of the fixture that the result decides gets an
     * automatic tick or cross. Returns how many stats rows were touched. [marks] is updated in place.
     */
    /** What you entered, with the matches file's corners/cards filling anything you left empty. */
    private fun effectiveExtras(typed: MatchExtras?, m: Match): MatchExtras? =
        typed?.orElse(m.extras) ?: m.extras

    private fun settleOne(
        league: League, date: String, home: String, away: String, hg: Int, ag: Int,
        extras: MatchExtras?, marks: MutableMap<String, PredictionRecord>
    ): Int {
        val fx = fixtureKey(league.id, date, home, away)
        var rows = repo.updateStatsForFixture(fx, { it == "score" }, StatsUpdater.scoreDeltas(home, away, hg, ag, repo.loadStats()))
        val played = Match(date, home, away, hg, ag)
        val analysis = analyze(home, away, null, date, true)
        for (pick in analysis.trackable) {
            val id = PredictionRecord.idOf(league.id, date, home, away, pick.source, pick.key)
            val existing = marks[id]
            if (existing != null && !existing.auto) continue // the user's own tick always wins
            val verdict = ResultJudge.judge(pick.key, home, away, hg, ag, extras)
            val statsFeed = pick.source == "Stats" && !StatsUpdater.isScoreDecided(pick.key)
            if (verdict == null) {
                if (existing != null) {
                    marks.remove(id)
                    if (statsFeed) rows += repo.updateStatsForFixture(fx, { it == "tick:${pick.key}" }, emptyList())
                }
                continue
            }
            marks[id] = PredictionRecord(
                id, league.id, league.name, date, home, away, pick.source, pick.key, pick.label, pick.group,
                verdict, System.currentTimeMillis(), auto = true
            )
            if (statsFeed) {
                val deltas = StatsUpdater.tickDeltas(pick.key, verdict, home, away, played, repo.loadStats())
                rows += repo.updateStatsForFixture(fx, { it == "tick:${pick.key}" }, deltas)
            }
        }
        // The weekly lists' picks for this game (Top 20 / Handicap / Over 1.5): judged from the result, automatic only.
        val recorded = listPicks.forGame(listPicks.load(), league.id, date, home, away)
        for (e in recorded) {
            val id = e.id
            val verdict = ResultJudge.judge(e.key, home, away, hg, ag, extras)
            if (verdict == null) { marks.remove(id); continue }
            marks[id] = PredictionRecord(
                id, league.id, league.name, date, home, away, e.source, e.key, e.label, e.group,
                verdict, System.currentTimeMillis(), auto = true
            )
        }
        return rows
    }

    fun removeScore(fixtureId: Int) {
        val league = _state.value.selected ?: return
        val item = _state.value.fixtures.firstOrNull { it.id == fixtureId } ?: return
        val home = item.home ?: return
        val away = item.away ?: return
        val date = item.fixture.date
        viewModelScope.launch {
            val (marks, extras) = withContext(Dispatchers.IO) {
                val fx = fixtureKey(league.id, date, home, away)
                repo.removeResult(league, Match(date, home, away, 0, 0))
                repo.updateStatsForFixture(fx, { it == "score" }, emptyList())
                // take back what this score did: its automatic ticks (and the stats those fed)
                val marks = _state.value.marks.toMutableMap()
                val prefix = "${league.id}|$date|$home|$away|"
                for (rec in marks.values.filter { it.id.startsWith(prefix) && it.auto }) {
                    marks.remove(rec.id)
                    if (rec.source == "Stats" && !StatsUpdater.isScoreDecided(rec.key)) {
                        repo.updateStatsForFixture(fx, { it == "tick:${rec.key}" }, emptyList())
                    }
                }
                results.save(marks.values)
                val extras = _state.value.extras.toMutableMap().also { it.remove(fx) }
                extrasStore.saveExtras(extras)
                extrasStore.saveSettled(extrasStore.loadSettled().toMutableMap().also { it.remove(fx) })
                marks.toMap() to extras.toMap()
            }
            _state.update { it.copy(marks = marks, extras = extras) }
            postMessage("Score removed; its automatic ticks and stats changes were taken back")
            reloadKeepingView(league)
        }
    }

    /** The model's most likely scorelines for a game, offered as quick picks in the result dialog. */
    fun scoreHints(home: String, away: String): List<ScoreCell> =
        if (home.isBlank() || away.isBlank()) emptyList()
        else PoissonEngine.predict(home, away, matches)?.topScores.orEmpty()

    /**
     * Your decision for a game whose hand-entered score differs from the matches file.
     * [useFile] = true: the file's score is used (your entry for that game is removed) and the game is settled again
     * with it; false: your entry stays and you aren't asked again unless the file's score for that game changes.
     * Corners, cards and first scorer you entered are kept either way.
     */
    fun resolveConflict(conflict: ScoreConflict, useFile: Boolean) {
        val league = _state.value.selected ?: return
        viewModelScope.launch {
            val marks = withContext(Dispatchers.IO) {
                if (useFile) {
                    repo.useFileScore(league, conflict)
                    // Settle the game again right away with the file's score (whatever the auto-settle start date is).
                    val updated = _state.value.marks.toMutableMap()
                    val fx = fixtureKey(league.id, conflict.date, conflict.home, conflict.away)
                    val x = _state.value.extras[fx]?.orElse(_state.value.scores[scoreKey(conflict.date, conflict.home, conflict.away)]?.extras)
                    settleOne(league, conflict.date, conflict.home, conflict.away, conflict.csvHome, conflict.csvAway, x, updated)
                    results.save(updated.values)
                    val settled = extrasStore.loadSettled().toMutableMap()
                    settled[fx] = settleSignature(conflict.csvHome, conflict.csvAway, x)
                    extrasStore.saveSettled(settled)
                    updated.toMap()
                } else {
                    repo.keepMyScore(league, conflict)
                    null
                }
            }
            if (marks != null) _state.update { it.copy(marks = marks) }
            postMessage(
                if (useFile) "Using the file's score ${conflict.csvHome}-${conflict.csvAway} for ${conflict.home} v ${conflict.away}"
                else "Keeping your score ${conflict.mineHome}-${conflict.mineAway} for ${conflict.home} v ${conflict.away}"
            )
            reloadKeepingView(league)
        }
    }

    /**
     * Scores that arrived through a matches file are settled like typed ones: stats updated, picks ticked.
     * Only games on or after [AppUiState.autoSettleFrom], once each, and only when the score changes.
     */
    private fun autoSettleLoaded(league: League) {
        if (!settling.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val st = _state.value
                val since = st.autoSettleFrom.ifBlank { repo.autoSettleFrom() }
                val now = java.time.LocalDateTime.now()
                val todo = st.fixtures.filter { fx ->
                    val h = fx.home; val a = fx.away
                    h != null && a != null && fx.fixture.date.isNotBlank() && fx.fixture.date >= since &&
                        DateUtils.isPlayed(fx.fixture.date, fx.fixture.time, now) &&
                        st.scores.containsKey(scoreKey(fx.fixture.date, h, a))
                }
                if (todo.isEmpty()) return@launch
                val changed = withContext(Dispatchers.IO) {
                    val settled = extrasStore.loadSettled().toMutableMap()
                    val marks = _state.value.marks.toMutableMap()
                    var did = 0
                    for (fx in todo) {
                        val h = fx.home ?: continue
                        val a = fx.away ?: continue
                        val m = st.scores[scoreKey(fx.fixture.date, h, a)] ?: continue
                        val key = fixtureKey(league.id, fx.fixture.date, h, a)
                        val x = effectiveExtras(st.extras[key], m)
                        val sig = settleSignature(m.homeGoals, m.awayGoals, x)
                        if (settled[key] == sig) continue
                        settleOne(league, fx.fixture.date, h, a, m.homeGoals, m.awayGoals, x, marks)
                        settled[key] = sig
                        did++
                    }
                    if (did > 0) {
                        results.save(marks.values)
                        extrasStore.saveSettled(settled)
                    }
                    did to marks.toMap()
                }
                if (changed.first > 0) {
                    _state.update { it.copy(marks = changed.second) }
                    postMessage("Settled ${changed.first} played ${if (changed.first == 1) "game" else "games"} from your matches file")
                    reloadKeepingView(league)
                }
            } finally {
                settling.set(false)
            }
        }
    }

    fun setAutoSettleFrom(date: String) {
        repo.setAutoSettleFrom(date)
        _state.update { it.copy(autoSettleFrom = date) }
        postMessage("Played games from $date on are settled automatically")
        _state.value.selected?.let { autoSettleLoaded(it) }
    }

    // ---- backup & restore ------------------------------------------------------------------

    fun backupTo(uri: Uri) {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { backup.write(it) }
                } catch (e: Exception) {
                    null
                }
            }
            postMessage(if (n != null) "Backup saved ($n files)" else "Couldn't save the backup")
        }
    }

    fun restoreFrom(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { backup.restore(it) }
                        ?: BackupManager.Restored.Failed("Couldn't open that file.")
                } catch (e: Exception) {
                    BackupManager.Restored.Failed("Couldn't read that file.")
                }
            }
            when (result) {
                is BackupManager.Restored.Failed -> postMessage(result.message)
                is BackupManager.Restored.Ok -> {
                    // re-read everything the backup replaced
                    val links = withContext(Dispatchers.IO) { linksStore.load() }
                    TeamMatcher.setUserLinks(links.map { it.a to it.b })
                    val leagues = repo.leagues()
                    val marks = withContext(Dispatchers.IO) { results.load() }
                    val extras = withContext(Dispatchers.IO) { extrasStore.loadExtras() }
                    invalidateCache()
                    _state.update { it.copy(leagues = leagues, marks = marks, nameLinks = links, extras = extras, autoSettleFrom = repo.autoSettleFrom()) }
                    val start = leagues.firstOrNull { it.id == repo.lastLeagueId() } ?: leagues.first()
                    selectLeague(start.id)
                    postMessage("Backup restored (${result.files} files)")
                }
            }
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
            modelBest = ConsensusEngine.modelBest(home, away, brymo, poisson, statsResult),
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
