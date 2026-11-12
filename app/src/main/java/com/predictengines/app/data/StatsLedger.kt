package com.predictengines.app.data

/** A change to one stats row caused by one fixture: [dv] added to the stat value, [dg] added to the games. */
data class StatDelta(val rowKey: String, val dv: Int, val dg: Int, val tag: String)

/**
 * Remembers every change a fixture made to the stats, so it can be undone exactly (a score edited, a tick
 * cleared) and never counted twice. [fx] = "leagueId|date|home|away"; [tag] = "score" or "tick:<pick key>".
 */
data class LedgerEntry(val fx: String, val rowKey: String, val dv: Int, val dg: Int, val tag: String)

fun fixtureKey(leagueId: String, date: String, home: String, away: String) = "$leagueId|$date|$home|$away"
/** True when the user marked this game postponed (and it has no score yet). [set] holds [fixtureKey]s. */
fun FixtureItem.isPostponed(leagueId: String, set: Set<String>): Boolean =
    set.isNotEmpty() && home != null && away != null && fixtureKey(leagueId, fixture.date, home, away) in set

fun scoreKey(date: String, home: String, away: String) = "$date|$home|$away"
