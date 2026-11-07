package com.predictengines.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.predictengines.app.data.Analysis
import com.predictengines.app.data.DataKind
import com.predictengines.app.data.DateUtils
import com.predictengines.app.data.Kryptonites
import com.predictengines.app.data.KryptoMode
import com.predictengines.app.data.ConsensusVote
import com.predictengines.app.data.ConsensusPick
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
import com.predictengines.app.data.PerformanceReport
import com.predictengines.app.data.RedEvent
import com.predictengines.app.data.PlayerStatus
import com.predictengines.app.data.PlayersStore
import com.predictengines.app.data.KeyPlayers
import com.predictengines.app.data.KeyPlayer
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val hiddenLeagues: List<League> = emptyList(),
    /** Leagues left out because another league has the same name and better files. */
    val duplicateLeagues: List<League> = emptyList(),
    /** What each Kryptonite does to flagged fixtures and picks (rule id -> mode). */
    val kryptoModes: Map<String, KryptoMode> = emptyMap(),
    val news: List<com.predictengines.app.data.TeamNews> = emptyList(),
    val kryptoConfig: com.predictengines.app.data.KryptoConfig = com.predictengines.app.data.KryptoConfig(),
    val players: List<KeyPlayer> = emptyList(),
    val answeredReds: Set<String> = emptySet(),
    val pendingReds: List<RedEvent> = emptyList(),
    val leagues: List<League> = emptyList(),
    val selected: League? = null,
    val isLoading: Boolean = true,
    /** What the loader is doing right now, shown under the loading animation. */
    val loadStep: String = "",
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
    /** Leagues whose count of played games without a result is being worked out right now. */
    val missingChecking: Set<String> = emptySet(),
    /** Picks that were on the weekly lists (Top 20 / Handicap / Over 1.5), settled or still waiting. */
    val listEntries: List<ListPicksStore.Entry> = emptyList(),
    val handicapPicks: List<TopPick> = emptyList(),
    val over15Picks: List<TopPick> = emptyList(),
    val lokiPicks: List<TopPick> = emptyList(),
    val cornerPicks: List<TopPick> = emptyList(),
    /** Trend picks (picks in league-and-market spots the data backs), the spots themselves, and the two limits that define "strong". */
    val trendPicks: List<TopPick> = emptyList(),
    val trendStrong: List<com.predictengines.app.data.Trends.Spot> = emptyList(),
    val trendCold: List<com.predictengines.app.data.Trends.Spot> = emptyList(),
    val trendConfig: com.predictengines.app.data.Trends.Config = com.predictengines.app.data.Trends.Config(),
    /** How the Trends filter would have done on past picks (each judged only by what was known before its game). */
    val trendBacktest: com.predictengines.app.data.Trends.Backtest? = null,
    val lokiConfig: com.predictengines.app.data.LokiConfig = com.predictengines.app.data.LokiConfig(),

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
    val message: String? = null,
    /** True from the moment a matches file is imported (or cleared) until its results are settled and everything is refreshed. */
    val importBusy: Boolean = false,
    /** What the Data tab says about the last matches import: the step it is on, then a summary. */
    val matchesOp: MatchesOp? = null,
    /** The last "Check flags" result on the Data tab, and whether one is running. */
    val kryptoAudit: com.predictengines.app.data.KryptoAudit.Report? = null,
    val kryptoAuditBusy: Boolean = false
) {
    /** The full-screen loader shows while a league loads and while an import is still settling. */
    val showLoader: Boolean get() = isLoading || importBusy
}

/** The Data tab's note about a matches import: [text] while [working], then the [summary] (kept until the next import or restart). */
data class MatchesOp(val leagueId: String, val working: Boolean, val text: String = "", val summary: String = "")

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LeagueRepository(application)
    private val disk = LeagueDiskCache(application)
    private val backup = BackupManager(application)
    private val extrasStore = ResultExtrasStore(application)
    private val listPicks = ListPicksStore(application)
    private val playersStore = PlayersStore(application)
    @Volatile private var savedPlayers = playersStore.load()
    private val settling = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastSettledCount = 0
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
    private var lokiCache: List<TopPick> = emptyList()
    private var cornerCache: List<TopPick> = emptyList()
    private var trendCands: List<TopPick> = emptyList()
    private var missingCache: Map<String, Int> = emptyMap()

    private fun invalidateCache() {
        cacheGen.incrementAndGet()
        cache.clear()
        topCache = null
        topJob?.cancel()
    }

    private fun step(text: String) {
        _state.update { if (it.loadStep == text) it else it.copy(loadStep = text) }
    }

    /** Reads and scores one league (slow: parses files and predicts every fixture). Cached by [load]. */
    private fun computeLeague(league: League): LoadedData {
        val gen = cacheGen.get()
        step("${league.name} · checking saved work")
        // Same inputs as last time (nothing edited, app not updated)? Reuse what was worked out then.
        val fingerprint = repo.fingerprint(league) + "|k" + KeyPlayers.stamp(savedPlayers.players, league.id) + "|n" + KeyPlayers.newsStamp(savedPlayers.news, league.id)
        disk.load(league.id, fingerprint)?.let { saved ->
            if (gen == cacheGen.get()) cache[league.id] = saved
            return saved
        }
        step("${league.name} · reading the matches file")
        val m = repo.loadMatches(league)
        val teams = m.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinct().sorted()
        step("${league.name} · reading stats and fixtures")
        val st = repo.loadStats()
        val led = repo.loadLedger()
        val items = buildFixtureItems(repo.loadFixtures(league), m, teams, st, league.id, led, league.name)
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
        _state.update { it.copy(players = savedPlayers.players, answeredReds = savedPlayers.answered, news = savedPlayers.news, leagues = leagues, kryptoModes = repo.kryptoModes(), kryptoConfig = repo.loadKryptoConfig(), lokiConfig = repo.loadLokiConfig(), hiddenLeagues = repo.hiddenLeagues(), duplicateLeagues = repo.duplicateLeagues(), marks = results.load(), nameLinks = links, extras = extrasStore.loadExtras(), autoSettleFrom = repo.autoSettleFrom(), listEntries = listPicks.load().values.toList()) }
        // the last known counts show at once; they are refreshed straight away, without scoring anything
        _state.update { it.copy(missingByLeague = repo.loadMissingCounts().filterKeys { id -> leagues.any { l -> l.id == id } }) }
        val start = leagues.firstOrNull { it.id == repo.lastLeagueId() } ?: leagues.first()
        selectLeague(start.id)
        refreshMissing()
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

    private fun load(league: League, resetTeams: Boolean, fresh: Boolean = false, awaitSettle: Boolean = false): Job {
        loadJob?.cancel()
        if (fresh) invalidateCache()
        val job = viewModelScope.launch {
            val cached = cache[league.id]
            if (cached == null) _state.update { it.copy(isLoading = true, loadStep = "") }
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
            refreshReds()
            refreshMissing(league)
            refreshTopPicks()
            val settle = autoSettleLoaded(league)
            if (awaitSettle) settle?.join()
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
            val lists = composeLists(cached, handicapCache, over15Cache)
            val trends = buildTrends(trendCands)
            _state.update {
                it.copy(
                    topPicks = lists.first, topPicksLoading = false,
                    handicapPicks = lists.second, over15Picks = lists.third,
                    lokiPicks = composeLoki(lokiCache),
                    cornerPicks = composeCorners(cornerCache),
                    trendPicks = trends.shown, trendStrong = trends.found.strong, trendCold = trends.found.cold,
                    trendConfig = repo.loadTrendConfig(), trendBacktest = trends.backtest,
                    missingByLeague = missingCache
                )
            }
            return
        }
        topJob?.cancel()
        topJob = viewModelScope.launch {
            _state.update { it.copy(topPicksLoading = true, loadStep = if (it.importBusy) it.loadStep else "") }
            val leagues = _state.value.leagues
            val gen = cacheGen.get()
            var held = Triple<List<TopPick>, List<TopPick>, List<TopPick>>(emptyList(), emptyList(), emptyList())
            var loki = emptyList<TopPick>()
            var corners = emptyList<TopPick>()
            var trendAll = emptyList<TopPick>()
            val top = withContext(Dispatchers.Default) {
                val all = ArrayList<TopPick>()
                val hcp = ArrayList<TopPick>()
                val o15 = ArrayList<TopPick>()
                val lk = ArrayList<TopPick>()
                val cr = ArrayList<TopPick>()
                val tr = ArrayList<TopPick>()
                val missing = HashMap<String, Int>()
                for ((li, league) in leagues.withIndex()) {
                    try {
                        if (!_state.value.importBusy) step("Top picks · league ${li + 1} of ${leagues.size}")
                        // Uses (and fills) the same per-league cache, so the other leagues are ready before you open them.
                        val data = cache[league.id] ?: computeLeague(league)
                        if (data.fixtures.isEmpty()) continue
                        missing[league.id] = countMissing(data)
                        if (data.matches.isEmpty()) continue
                        all += TopPicksEngine.candidates(league, data.fixtures)
                        hcp += TopPicksEngine.handicapCandidates(league, data.fixtures)
                        o15 += TopPicksEngine.over15Candidates(league, data.fixtures)
                        lk += TopPicksEngine.lokiCandidates(league, data.fixtures, data.matches)
                        cr += TopPicksEngine.cornerCandidates(league, data.fixtures)
                        tr += TopPicksEngine.trendCandidates(league, data.fixtures)
                    } catch (e: Exception) {
                        // one unreadable league must not hide the others
                    }
                }
                missingCache = missing
                if (!_state.value.importBusy) step("Top picks · ranking and checking Kryptonites")
                val modes = repo.kryptoModes()
                val shown = Triple(TopPicksEngine.rank(all, modes = modes), TopPicksEngine.rank(hcp, modes = modes), TopPicksEngine.rank(o15, modes = modes))
                held = Triple(
                    TopPicksEngine.heldBack(all, shown.first), TopPicksEngine.heldBack(hcp, shown.second), TopPicksEngine.heldBack(o15, shown.third)
                )
                // Loki ignores the Kryptonite Hide / Push-down switches: it needs the flagged fixtures
                loki = TopPicksEngine.rank(lk)
                corners = TopPicksEngine.rank(cr, modes = modes)
                trendAll = tr
                shown
            }
            withContext(Dispatchers.IO) { trendCands = trendAll
                recordListPicks(top.first, top.second, top.third, held, loki, corners, buildTrends(trendAll).ranked) }
            if (gen == cacheGen.get()) {
                topCache = top.first; handicapCache = top.second; over15Cache = top.third; lokiCache = loki; cornerCache = corners
            }
            val lists = withContext(Dispatchers.IO) { composeLists(top.first, top.second, top.third) }
            val lokiShown = withContext(Dispatchers.IO) { composeLoki(loki) }
            val cornersShown = withContext(Dispatchers.IO) { composeCorners(corners) }
            val trends = withContext(Dispatchers.IO) { buildTrends(trendAll) }
            _state.update {
                it.copy(
                    topPicks = lists.first, handicapPicks = lists.second, over15Picks = lists.third, lokiPicks = lokiShown, cornerPicks = cornersShown,
                    trendPicks = trends.shown, trendStrong = trends.found.strong, trendCold = trends.found.cold,
                    trendConfig = repo.loadTrendConfig(), trendBacktest = trends.backtest,
                    missingByLeague = missingCache, topPicksLoading = false
                )
            }
        }
    }

    /**
     * The weekly lists as shown: the ranked upcoming picks, plus the picks whose games have since been played, which
     * stay in their week (taken from what was written down when the list was built). Each week holds at most the
     * list's usual 20 upcoming-plus-played picks.
     */
    private fun composeLists(top: List<TopPick>, hcp: List<TopPick>, o15: List<TopPick>): Triple<List<TopPick>, List<TopPick>, List<TopPick>> {
        fun merge(source: String, upcoming: List<TopPick>) = mergePlayed(source, upcoming, perGame = true)
        return Triple(
            merge(ListPicksStore.TOP, top), merge(ListPicksStore.HANDICAP, hcp), merge(ListPicksStore.OVER15, o15)
        )
    }

    /** The Loki list as shown: a game can have several Loki picks, so played ones are kept per pick, not per game. */
    private fun composeLoki(loki: List<TopPick>): List<TopPick> = mergePlayed(ListPicksStore.LOKI, loki, perGame = false)

    /**
     * Trend picks: the strong league-and-market spots in your settled picks (from the weekly lists), and the upcoming picks
     * that fall in one. Returns the spots, the ranked upcoming picks (what gets written down) and the list as shown.
     */
    private class TrendBundle(
        val found: com.predictengines.app.data.Trends.Found, val ranked: List<TopPick>, val shown: List<TopPick>,
        val backtest: com.predictengines.app.data.Trends.Backtest
    )

    private fun buildTrends(cands: List<TopPick>): TrendBundle {
        val visible = _state.value.leagues.map { it.id }.toSet()
        val cfg = repo.loadTrendConfig()
        val marks = _state.value.marks.values
        val found = com.predictengines.app.data.Trends.spots(marks, visible, cfg)
        val ranked = TopPicksEngine.rank(com.predictengines.app.data.Trends.pick(cands, found.strong, cfg), modes = repo.kryptoModes())
        return TrendBundle(
            found, ranked, mergePlayed(ListPicksStore.TRENDS, ranked, perGame = false),
            com.predictengines.app.data.Trends.backtest(marks, visible, cfg)
        )
    }

    /** The Corners list as shown: one pick per game, played ones kept in their week. */
    private fun composeCorners(corners: List<TopPick>): List<TopPick> = mergePlayed(ListPicksStore.CORNERS, corners, perGame = true)

    private fun mergePlayed(source: String, upcoming: List<TopPick>, perGame: Boolean): List<TopPick> {
        val visible = _state.value.leagues.map { it.id }.toSet()
        val played = listPicks.load().values.filter { it.leagueId in visible && DateUtils.isPlayed(it.date, it.time) }
        // one pick per game: the one that first made the list
        val past = played.filter { it.source == source }
                .distinctBy { if (perGame) "${it.leagueId}|${it.date}|${it.home}|${it.away}" else it.id }.map { e ->
                TopPick(
                    leagueId = e.leagueId, leagueName = e.leagueName, date = e.date, time = e.time,
                    homeDisplay = e.homeDisplay.ifBlank { e.home }, awayDisplay = e.awayDisplay.ifBlank { e.away },
                    home = e.home, away = e.away,
                    pick = ConsensusPick(
                        key = e.key, label = e.label, group = e.group,
                        backers = e.backers.map { (m, c) -> ConsensusVote(m, c) },
                        against = emptyList(), abstain = emptyList(), modelCount = e.modelCount, strength = e.strength
                    )
                )
            }
        val live = upcoming.filter { !DateUtils.isPlayed(it.date, it.time) }
        return TopPicksEngine.withPlayed(live, past, modes = if (source == ListPicksStore.LOKI) emptyMap() else _state.value.kryptoModes)
    }

    /**
     * Counts, for each league (or just [only]), the played games that have no result yet, from the fixtures and results
     * files alone (no predictions), all leagues at the same time. The counts are kept for the next start.
     */
    private fun refreshMissing(only: League? = null) {
        val leagues = if (only != null) listOf(only) else _state.value.leagues
        if (leagues.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(missingChecking = it.missingChecking + leagues.map { l -> l.id }) }
            val counts = withContext(Dispatchers.IO) {
                leagues.map { l -> async { l.id to runCatching { countMissingFor(l) }.getOrDefault(0) } }.awaitAll().toMap()
            }
            _state.update { it.copy(missingByLeague = it.missingByLeague + counts, missingChecking = it.missingChecking - counts.keys) }
            withContext(Dispatchers.IO) { repo.saveMissingCounts(_state.value.missingByLeague) }
        }
    }

    private fun countMissingFor(league: League): Int {
        val fixtures = repo.loadFixtures(league)
        if (fixtures.isEmpty()) return 0
        val played = repo.loadMatches(league)
        if (played.isEmpty()) return 0
        val teams = played.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinct().sorted()
        val known = played.mapTo(HashSet()) { scoreKey(it.date, it.homeTeam, it.awayTeam) }
        val now = java.time.LocalDateTime.now()
        return fixtures.count { f ->
            val h = TeamMatcher.resolve(f.homeTeam, teams)
            val a = TeamMatcher.resolve(f.awayTeam, teams)
            h != null && a != null && f.date.isNotBlank() && DateUtils.isPlayed(f.date, f.time, now) &&
                scoreKey(f.date, h, a) !in known
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
    private fun recordListPicks(
        top: List<TopPick>, hcp: List<TopPick>, o15: List<TopPick>,
        held: Triple<List<TopPick>, List<TopPick>, List<TopPick>> = Triple(emptyList(), emptyList(), emptyList()),
        loki: List<TopPick> = emptyList(),
        corners: List<TopPick> = emptyList(),
        trends: List<TopPick> = emptyList()
    ) {
        fun entries(source: String, list: List<TopPick>) = list.map { tp ->
            ListPicksStore.Entry(
                source, tp.leagueId, tp.leagueName, tp.date, tp.home, tp.away,
                tp.pick.key, tp.pick.label, tp.pick.group, tp.time,
                tp.homeDisplay, tp.awayDisplay, tp.pick.strength,
                tp.pick.backers.map { it.model to it.confidence }, tp.pick.modelCount,
                com.predictengines.app.data.Kryptonites.relevant(tp.flags, tp.pick.group).map { f -> f.ruleId }.distinct()
            )
        }
        val hide = PerformanceReport.HIDDEN_SUFFIX
        listPicks.add(
            entries(ListPicksStore.TOP, top) + entries(ListPicksStore.HANDICAP, hcp) + entries(ListPicksStore.OVER15, o15) +
                entries(ListPicksStore.TOP + hide, held.first) + entries(ListPicksStore.HANDICAP + hide, held.second) +
                entries(ListPicksStore.OVER15 + hide, held.third) + entries(ListPicksStore.LOKI, loki) + entries(ListPicksStore.CORNERS, corners) + entries(ListPicksStore.TRENDS, trends)
        )
        _state.update { it.copy(listEntries = listPicks.load().values.toList()) }
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
        led: List<LedgerEntry> = emptyList(),
        leagueName: String = ""
    ): List<FixtureItem> {
        val sorted = fixtures.sortedBy { DateUtils.sortKey(it.date, it.time) }
        return sorted.mapIndexed { index, f ->
            if (leagueName.isNotEmpty() && index % 4 == 0) step("$leagueName · scoring fixture ${index + 1} of ${sorted.size}")
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
                val consensus = ConsensusEngine.compute(home, away, brymo, poisson, stats)
                FixtureSummary(
                    consensus,
                    ConsensusEngine.handicapPick(home, away, brymo),
                    ConsensusEngine.over15Backers(home, away, poisson, stats),
                    Kryptonites.forFixture(
                        home, away, before, f.time, f.date, consensus,
                        savedPlayers.players.filter { it.leagueId == leagueId },
                        savedPlayers.news.filter { it.leagueId == leagueId }
                    ),
                    ConsensusEngine.cornerBest(home, away, poisson, stats)
                )
            } else null
            FixtureItem(index, f, home, away, summary)
        }
    }

    // ---- importing -------------------------------------------------------------------------

    private fun scoreMap(ms: List<Match>) = ms.associate { "${it.date}|${it.homeTeam}|${it.awayTeam}" to (it.homeGoals to it.awayGoals) }

    /** New, changed and removed games between two sets of results (keyed by date and teams). */
    private fun compareResults(before: List<Match>, after: List<Match>): Triple<Int, Int, Int> {
        val b = scoreMap(before); val a = scoreMap(after)
        val added = a.keys.count { it !in b }
        val changed = a.count { (k, v) -> b[k] != null && b[k] != v }
        val removed = b.keys.count { it !in a }
        return Triple(added, changed, removed)
    }

    private fun setOp(league: League, text: String) {
        _state.update { it.copy(importBusy = true, loadStep = text, matchesOp = MatchesOp(league.id, true, text)) }
    }

    /** Recounts the selected league's Kryptonite flags from the matches file as it is now, and compares them with what is shown. */
    fun checkKryptonites() {
        val league = _state.value.selected ?: return
        viewModelScope.launch {
            _state.update { it.copy(kryptoAuditBusy = true) }
            val report = withContext(Dispatchers.IO) {
                runCatching {
                    val all = repo.loadMatches(league)
                    val teams = all.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinct().sorted()
                    val shown = _state.value.fixtures
                    val now = java.time.LocalDateTime.now()
                    val refs = repo.loadFixtures(league).filter { !DateUtils.isPlayed(it.date, it.time, now) }.map { f ->
                        val h = TeamMatcher.resolve(f.homeTeam, teams)
                        val a = TeamMatcher.resolve(f.awayTeam, teams)
                        val ok = h != null && a != null && h != a
                        val stored = shown.firstOrNull {
                            it.fixture.date == f.date && it.fixture.homeTeam == f.homeTeam && it.fixture.awayTeam == f.awayTeam
                        }?.summary?.flags
                        com.predictengines.app.data.KryptoAudit.FixtureRef(f.date, f.homeTeam, f.awayTeam, if (ok) h else null, if (ok) a else null, stored)
                    }
                    com.predictengines.app.data.KryptoAudit.check(
                        league.id, league.name, refs, all,
                        savedPlayers.players.filter { it.leagueId == league.id },
                        savedPlayers.news.filter { it.leagueId == league.id }
                    )
                }.getOrNull()
            }
            _state.update {
                it.copy(
                    kryptoAudit = report, kryptoAuditBusy = false,
                    message = if (report == null) "Could not check the flags (no matches file for ${league.name}?)." else it.message
                )
            }
        }
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    fun importFile(kind: DataKind, uri: Uri) {
        val league = _state.value.selected ?: return
        viewModelScope.launch {
            val isMatches = kind == DataKind.MATCHES
            if (isMatches) setOp(league, "${league.name} \u00b7 reading the matches file")
            try {
                val before = if (isMatches) withContext(Dispatchers.IO) { runCatching { repo.loadMatches(league) }.getOrDefault(emptyList()) } else emptyList()
                val result = withContext(Dispatchers.IO) { repo.importFile(league, kind, uri) }
                when (result) {
                    is ImportResult.Success -> {
                        val what = when (kind) {
                            DataKind.MATCHES -> "matches"
                            DataKind.FIXTURES -> "fixtures"
                            DataKind.STATS -> "stat rows (merged)"
                        }
                        val skippedN = result.info.skipped
                        val skipped = if (skippedN > 0) " ($skippedN rows skipped)" else ""
                        val columns = result.note?.let { " \u00b7 $it" }.orEmpty()
                        if (!isMatches) {
                            postMessage("Loaded ${result.info.rows} $what from ${result.info.fileName}$skipped$columns")
                            load(league, resetTeams = false, fresh = true)
                            return@launch
                        }
                        val after = withContext(Dispatchers.IO) { runCatching { repo.loadMatches(league) }.getOrDefault(emptyList()) }
                        val (added, changed, _) = compareResults(before, after)
                        val known = (result.info.rows - added - changed).coerceAtLeast(0)
                        setOp(league, "${league.name} \u00b7 importing ${result.info.rows} matches from ${result.info.fileName}")
                        postMessage("Loaded ${result.info.rows} $what from ${result.info.fileName}: $added new$skipped$columns")
                        lastSettledCount = 0
                        load(league, resetTeams = true, fresh = true, awaitSettle = true).join()
                        val settled = lastSettledCount
                        val summary = "${league.name}: ${result.info.rows} matches in the file \u00b7 $added new \u00b7 $changed ${plural(changed, "score", "scores")} changed" +
                            " \u00b7 $known already known" + (if (skippedN > 0) " \u00b7 $skippedN rows skipped" else "") +
                            (if (settled > 0) " \u00b7 $settled played ${plural(settled, "game", "games")} settled" else "")
                        _state.update { it.copy(matchesOp = MatchesOp(league.id, false, summary = summary)) }
                    }
                    is ImportResult.Failure -> {
                        postMessage(result.message)
                        _state.update { it.copy(matchesOp = null) }
                    }
                }
            } finally {
                _state.update { it.copy(importBusy = false) }
            }
        }
    }

    fun clearImport(kind: DataKind) {
        val league = _state.value.selected ?: return
        viewModelScope.launch {
            val isMatches = kind == DataKind.MATCHES
            if (isMatches) setOp(league, "${league.name} \u00b7 restoring the results")
            try {
                val before = if (isMatches) withContext(Dispatchers.IO) { runCatching { repo.loadMatches(league) }.getOrDefault(emptyList()) } else emptyList()
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
                if (!isMatches) {
                    load(league, resetTeams = false, fresh = true)
                    return@launch
                }
                val after = withContext(Dispatchers.IO) { runCatching { repo.loadMatches(league) }.getOrDefault(emptyList()) }
                val (added, changed, removed) = compareResults(before, after)
                lastSettledCount = 0
                load(league, resetTeams = true, fresh = true, awaitSettle = true).join()
                val settled = lastSettledCount
                val summary = "${league.name}: " + (if (league.assetFile != null) "back to the built-in file" else "results removed") +
                    " \u00b7 ${after.size} matches \u00b7 $added new \u00b7 $changed ${plural(changed, "score", "scores")} changed \u00b7 $removed no longer there" +
                    (if (settled > 0) " \u00b7 $settled played ${plural(settled, "game", "games")} settled" else "")
                _state.update { it.copy(matchesOp = MatchesOp(league.id, false, summary = summary)) }
            } finally {
                _state.update { it.copy(importBusy = false) }
            }
        }
    }

    /** One tap: this league's imported files, entered scores, their settled ticks and stats changes are wiped. */
    fun resetLeague(league: League) {
        viewModelScope.launch {
            val marks = withContext(Dispatchers.IO) {
                repo.resetLeague(league)
                val prefix = "${league.id}|"
                val kept = _state.value.marks.filterValues { !(it.id.startsWith(prefix) && it.auto) }
                results.save(kept.values)
                extrasStore.saveExtras(extrasStore.loadExtras().filterKeys { !it.startsWith(prefix) })
                extrasStore.saveSettled(extrasStore.loadSettled().filterKeys { !it.startsWith(prefix) })
                kept
            }
            invalidateCache()
            _state.update { it.copy(marks = marks, extras = extrasStore.loadExtras()) }
            postMessage("${league.name} was reset to its built-in data")
            if (_state.value.selected?.id == league.id) load(league, resetTeams = true, fresh = true) else refreshTopPicks()
        }
    }

    // ---- custom leagues --------------------------------------------------------------------

    fun addLeague(name: String) {
        if (name.isBlank()) return
        if (repo.nameTaken(name)) {
            postMessage("A league called \"${name.trim()}\" already exists. Two leagues can't share a name.")
            return
        }
        val league = repo.addCustomLeague(name)
        _state.update { it.copy(leagues = repo.leagues(), hiddenLeagues = repo.hiddenLeagues(), duplicateLeagues = repo.duplicateLeagues()) }
        selectLeague(league.id)
        postMessage("League added. Now load its match results file.")
    }

    fun deleteLeague(league: League) {
        if (!league.isCustom) return
        repo.deleteCustomLeague(league)
        invalidateCache()
        val leagues = repo.leagues()
        _state.update { it.copy(leagues = leagues, hiddenLeagues = repo.hiddenLeagues(), duplicateLeagues = repo.duplicateLeagues()) }
        if (_state.value.selected?.id == league.id) selectLeague(leagues.first().id)
        postMessage("Deleted ${league.name}")
    }

    /** Takes a league out of the app's lists (chips, Top 20, reports of the open league...). Nothing is deleted. */
    fun hideLeague(league: League) {
        if (_state.value.leagues.size <= 1) {
            postMessage("Keep at least one league showing")
            return
        }
        repo.hideLeague(league)
        invalidateCache()
        val leagues = repo.leagues()
        _state.update { it.copy(leagues = leagues, hiddenLeagues = repo.hiddenLeagues(), duplicateLeagues = repo.duplicateLeagues()) }
        if (_state.value.selected?.id == league.id) selectLeague(leagues.first().id) else refreshTopPicks()
        postMessage("${league.name} is hidden. Bring it back under Data > Leagues.")
    }

    fun showLeague(league: League) {
        repo.showLeague(league)
        invalidateCache()
        _state.update { it.copy(leagues = repo.leagues(), hiddenLeagues = repo.hiddenLeagues(), duplicateLeagues = repo.duplicateLeagues()) }
        refreshTopPicks()
        postMessage("${league.name} is showing again")
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
    private fun reloadKeepingView(league: League): Job {
        val reopen = _state.value.openFixtureId
        val hadManual = _state.value.manualAnalysis != null
        return viewModelScope.launch {
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
            val allowed = withContext(Dispatchers.IO) { date >= repo.autoSettleFrom() && !repo.countedUnderAnotherKey(fx) }
            if (!allowed) {
                if (correct != null) postMessage("Not added to the stats: this game is already in the stats file (played before ${repo.autoSettleFrom()}) or already counted")
                return@launch
            }
            val changed = withContext(Dispatchers.IO) {
                val deltas = if (correct == null) emptyList()
                else StatsUpdater.tickDeltas(pick.key, correct, home, away, score, repo.loadStats())
                repo.updateStatsForFixture(fx, { it == tag }, deltas)
            }
            if (correct != null) postMessage(if (changed > 0) "Stats data updated ($changed rows)" else "No stats rows to update for this pick")
            reloadKeepingView(league)
        }
    }

    // ---- key players -----------------------------------------------------------------------

    /** Red cards of the open league's teams (last game, recent) that still wait for "who was sent off?". */
    private fun refreshReds() {
        val league = _state.value.selected ?: return
        val pending = KeyPlayers.pendingReds(league.id, matches, savedPlayers.answered, java.time.LocalDate.now())
        _state.update { it.copy(pendingReds = pending, players = savedPlayers.players, answeredReds = savedPlayers.answered, news = savedPlayers.news) }
    }

    private fun persistPlayers(next: PlayersStore.Saved) {
        savedPlayers = next
        playersStore.save(next)
        _state.update { it.copy(players = next.players, answeredReds = next.answered, news = next.news) }
    }

    private fun afterPlayersChanged() {
        invalidateCache()
        _state.value.selected?.let { reloadKeepingView(it) }
    }

    /** Adds or updates a key player (with a new photo when [photoUri] is given). */
    fun savePlayer(player: KeyPlayer, photoUri: Uri?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var p = player
                if (photoUri != null &&
                    playersStore.savePhoto(p.id, getApplication<Application>().contentResolver, photoUri)
                ) p = p.copy(photoStamp = System.currentTimeMillis())
                if (p.status == PlayerStatus.SUSPENDED && p.banGames > 0 && p.banFrom.isBlank()) {
                    p = p.copy(banFrom = java.time.LocalDate.now().toString())
                }
                persistPlayers(savedPlayers.copy(players = savedPlayers.players.filter { it.id != p.id } + p))
            }
            afterPlayersChanged()
            postMessage("Saved ${player.name}")
        }
    }

    fun deletePlayer(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playersStore.deletePhoto(id)
                persistPlayers(savedPlayers.copy(players = savedPlayers.players.filter { it.id != id }))
            }
            afterPlayersChanged()
        }
    }

    /** Adds or updates a piece of team news; the team's flags are rebuilt. */
    fun saveNews(item: com.predictengines.app.data.TeamNews) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                persistPlayers(savedPlayers.copy(news = savedPlayers.news.filter { it.id != item.id } + item))
            }
            afterPlayersChanged()
            postMessage("Saved: ${item.team} · ${item.type.label}")
        }
    }

    fun deleteNews(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { persistPlayers(savedPlayers.copy(news = savedPlayers.news.filter { it.id != id })) }
            afterPlayersChanged()
        }
    }

    /** Answers "who was sent off?": a key player (suspended for [games] games from that game) or nobody listed. */
    fun answerRed(event: RedEvent, playerId: String?, games: Int) {
        // the prompt closes at once; the reload below would only take it away after the whole league is recomputed
        _state.update { it.copy(pendingReds = it.pendingReds.filter { r -> r.key != event.key }) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val players = if (playerId == null) savedPlayers.players else savedPlayers.players.map {
                    if (it.id == playerId) it.copy(
                        status = PlayerStatus.SUSPENDED, banGames = games.coerceIn(1, 50), banFrom = event.date, returnDate = ""
                    ) else it
                }
                persistPlayers(savedPlayers.copy(players = players, answered = savedPlayers.answered + event.key))
            }
            afterPlayersChanged()
            postMessage(if (playerId == null) "Noted: someone else was sent off" else "Suspension saved")
        }
    }

    /** Chooses what a Kryptonite does: warn only, push flagged picks to the bottom of their week, or hide them. */
    fun setKryptoMode(ruleId: String, mode: KryptoMode) {
        repo.setKryptoMode(ruleId, mode)
        _state.update { it.copy(kryptoModes = repo.kryptoModes()) }
        topCache = null
        refreshTopPicks()
        postMessage("Kryptonite set to: ${mode.label}")
    }

    fun setLokiOn(id: String, on: Boolean) {
        repo.setLokiOn(id, on)
        _state.update { it.copy(lokiConfig = repo.loadLokiConfig()) }
        topCache = null
        refreshTopPicks()
    }

    /** Trends: how many settled picks and what score a league-and-market spot needs to count as strong. */
    fun setTrend(config: com.predictengines.app.data.Trends.Config) {
        repo.saveTrendConfig(config)
        _state.update { it.copy(trendConfig = repo.loadTrendConfig()) }
        refreshTopPicks()
    }

    fun setLokiParam(id: String, value: Int) {
        repo.setLokiParam(id, value)
        _state.update { it.copy(lokiConfig = repo.loadLokiConfig()) }
        topCache = null
        refreshTopPicks()
    }

    /** Re-reads the Kryptonite numbers and pick types, and rebuilds the flags when numbers changed. */
    private fun kryptoConfigChanged(numbersChanged: Boolean) {
        _state.update { it.copy(kryptoModes = repo.kryptoModes(), kryptoConfig = repo.loadKryptoConfig()) }
        if (numbersChanged) afterPlayersChanged() else { topCache = null; refreshTopPicks() }
    }

    /** Takes a flag off its team until the rule triggers again (the next occurrence has a new date). */
    fun dismissKryptoFlag(flag: com.predictengines.app.data.KryptoFlag) {
        val key = flag.dismissKey
        if (key.isEmpty()) return
        repo.dismissKryptoFlag(key)
        kryptoConfigChanged(true)
        postMessage("Flag dismissed")
    }

    fun restoreDismissedFlags() {
        repo.clearDismissedFlags()
        kryptoConfigChanged(true)
        postMessage("Dismissed flags are back")
    }

    fun setKryptoParam(ruleId: String, key: String, value: Int) {
        repo.setKryptoParam(ruleId, key, value)
        kryptoConfigChanged(true)
    }

    fun setKryptoMarkets(ruleId: String, markets: Set<String>) {
        repo.setKryptoMarkets(ruleId, markets)
        kryptoConfigChanged(false)
    }

    fun resetKryptoRule(rule: com.predictengines.app.data.KryptoRule) {
        repo.resetKryptoRule(rule)
        kryptoConfigChanged(true)
        postMessage("${rule.name} set back to its defaults")
    }

    /** Takes back stats changes that were counted twice for one game. */
    fun repairStatsChanges() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { repo.repairLedger() }
            postMessage(if (n == 0) "Checked: no stats change was counted twice" else "Fixed: $n duplicate stats changes taken back")
            _state.value.selected?.let { reloadKeepingView(it) }
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
        // The stats change only for a game that is newer than the stats file and not yet counted under another key.
        val countStats = date >= repo.autoSettleFrom() && !repo.countedUnderAnotherKey(fx)
        var rows = if (countStats) repo.updateStatsForFixture(fx, { it == "score" }, StatsUpdater.scoreDeltas(home, away, hg, ag, repo.loadStats())) else 0
        val played = Match(date, home, away, hg, ag)
        val analysis = analyze(home, away, null, date, true)
        for (pick in analysis.trackable) {
            val id = PredictionRecord.idOf(league.id, date, home, away, pick.source, pick.key)
            val existing = marks[id]
            if (existing != null && !existing.auto) continue // the user's own tick always wins
            val verdict = ResultJudge.judge(pick.key, home, away, hg, ag, extras)
            val statsFeed = countStats && pick.source == "Stats" && !StatsUpdater.isScoreDecided(pick.key)
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
    private fun autoSettleLoaded(league: League): Job? {
        if (!settling.compareAndSet(false, true)) return null
        return viewModelScope.launch {
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
                step("${league.name} · settling ${todo.size} played ${if (todo.size == 1) "game" else "games"}")
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
                    lastSettledCount = changed.first
                    _state.update { it.copy(marks = changed.second) }
                    postMessage("Settled ${changed.first} played ${if (changed.first == 1) "game" else "games"} from your matches file")
                    step("${league.name} · ticking results and updating predictions")
                    reloadKeepingView(league).join()
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
                    savedPlayers = withContext(Dispatchers.IO) { playersStore.load() }
                    invalidateCache()
                    _state.update { it.copy(players = savedPlayers.players, answeredReds = savedPlayers.answered, news = savedPlayers.news, leagues = leagues, kryptoModes = repo.kryptoModes(), kryptoConfig = repo.loadKryptoConfig(), lokiConfig = repo.loadLokiConfig(), hiddenLeagues = repo.hiddenLeagues(), duplicateLeagues = repo.duplicateLeagues(), marks = marks, nameLinks = links, extras = extras, autoSettleFrom = repo.autoSettleFrom(), listEntries = listPicks.load().values.toList()) }
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
