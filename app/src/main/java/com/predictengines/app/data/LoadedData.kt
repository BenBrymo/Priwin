package com.predictengines.app.data

/** Everything one league needs on screen, worked out once and then kept (in memory and on disk). */
class LoadedData(
    val matches: List<Match>,
    val teams: List<String>,
    val fixtures: List<FixtureItem>,
    val matchesImport: ImportInfo?,
    val fixturesImport: ImportInfo?,
    val builtInFixtures: Boolean,
    val stats: List<StatRow>,
    val statsImport: ImportInfo?,
    val added: List<Match>,
    val ledger: List<LedgerEntry>
)
