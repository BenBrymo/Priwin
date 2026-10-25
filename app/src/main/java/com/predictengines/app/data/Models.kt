package com.predictengines.app.data

/** A single played result, used to derive both prediction engines. */
data class Match(
    val date: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeGoals: Int,
    val awayGoals: Int
)

/** An upcoming (not yet played) fixture loaded from a fixtures file. */
data class Fixture(
    val date: String,
    val time: String,
    val homeTeam: String,
    val awayTeam: String
)

/**
 * A league. Built-in leagues are detected from the files in assets (see [LeagueCatalog]) and usually ship
 * with a historical results file ([assetFile]); custom leagues are created by the user and get all their
 * data from imported files.
 */
data class League(
    val id: String,
    val name: String,
    val assetFile: String? = null,
    val isCustom: Boolean = false
)

enum class DataKind { MATCHES, FIXTURES, STATS }

/** Bookkeeping for a file the user imported from phone storage. */
data class ImportInfo(
    val fileName: String,
    val rows: Int,
    val skipped: Int,
    val importedAtMillis: Long
)

/** One labeled outcome + confidence, produced by the Brymo engine. */
data class Prediction(
    val label: String,
    val confidencePercent: Double
)

/** A cell in the Poisson correct-score list. */
data class ScoreCell(
    val homeGoals: Int,
    val awayGoals: Int,
    val probabilityPercent: Double
)

data class BrymoResult(
    val homeTags: List<String>,
    val awayTags: List<String>,
    val all: List<Prediction>,
    val best: List<Prediction>
)

data class PoissonResult(
    val expectedHomeGoals: Double,
    val expectedAwayGoals: Double,
    val homeWinPercent: Double,
    val drawPercent: Double,
    val awayWinPercent: Double,
    val homeOrDrawPercent: Double,
    val awayOrDrawPercent: Double,
    val homeOrAwayPercent: Double,
    val over15Percent: Double,
    val over25Percent: Double,
    val over35Percent: Double,
    val under25Percent: Double,
    val bttsYesPercent: Double,
    val bttsNoPercent: Double,
    val topScores: List<ScoreCell>,
    /** scoreGrid[homeGoals][awayGoals] as percentages, 0..4 goals each. */
    val scoreGrid: List<List<Double>>,
    val homeGamesUsed: Int,
    val awayGamesUsed: Int
)

data class TeamStats(
    val teamName: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val avgGoalsFor: Double,
    val avgGoalsAgainst: Double,
    val bttsRatePercent: Double
)

/** Everything the UI needs to show both models for one matchup. */
data class Analysis(
    val homeTeam: String,
    val awayTeam: String,
    val kickoff: String?,
    val brymo: BrymoResult,
    val poisson: PoissonResult?,
    val homeStats: TeamStats,
    val awayStats: TeamStats,
    /** Null when no stats file has been loaded. */
    val stats: StatsResult? = null,
    val consensus: ConsensusResult = ConsensusResult(emptyList(), emptyList()),
    /** Fixture date (yyyy-MM-dd) when this analysis is for a fixture; blank for a manual matchup. */
    val date: String = "",
    /** What the user can tick or cross. Empty for a manual matchup. */
    val trackable: List<TrackablePick> = emptyList(),
    /** Each model's three best calls (Brymo / Stats: 80%+; Poisson: highest probability), keyed by model name. */
    val modelBest: Map<String, List<Prediction>> = emptyMap()
)

/** What a fixture card in the list shows: only the consensus pick(s) across the models. */
data class FixtureSummary(
    val consensus: ConsensusResult
)

/**
 * A fixture from the fixtures file, with its team names resolved to the names used
 * in the matches file ([home]/[away] are null when no match could be found).
 */
data class FixtureItem(
    val id: Int,
    val fixture: Fixture,
    val home: String?,
    val away: String?,
    val summary: FixtureSummary?
)
