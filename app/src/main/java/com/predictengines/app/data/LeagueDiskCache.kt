package com.predictengines.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saves a league's finished [LoadedData] on the phone so the next app start doesn't repeat the slow work
 * (reading files, merging team names, predicting every fixture). A saved copy is only used when its
 * fingerprint, built from every input file's size and time plus the app's install time, is unchanged;
 * any edit, import, score or app update makes it miss and the league is rebuilt and saved again.
 * A damaged or unreadable cache file is ignored, never fatal.
 */
class LeagueDiskCache(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "league_cache")

    private fun fileFor(leagueId: String) = File(dir, leagueId.filter { it.isLetterOrDigit() || it == '_' || it == '-' } + ".json")

    fun load(leagueId: String, fingerprint: String): LoadedData? = try {
        val f = fileFor(leagueId)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText(Charsets.UTF_8))
            if (o.getString("fp") != fingerprint) null else decode(o)
        }
    } catch (e: Exception) {
        null
    }

    fun save(leagueId: String, fingerprint: String, data: LoadedData) {
        try {
            dir.mkdirs()
            val o = encode(data).put("fp", fingerprint)
            val tmp = File(dir, "tmp_${System.nanoTime()}")
            tmp.writeText(o.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(fileFor(leagueId))) tmp.delete()
        } catch (e: Exception) {
            // caching is only an optimisation
        }
    }

    // ---- encoding --------------------------------------------------------------------------

    private fun encode(d: LoadedData) = JSONObject()
        .put("matches", arr(d.matches) { m -> encMatch(m) })
        .put("teams", JSONArray(d.teams))
        .put("fixtures", arr(d.fixtures) { fx -> encodeFixture(fx) })
        .put("mi", d.matchesImport?.let { info(it) } ?: JSONObject.NULL)
        .put("fi", d.fixturesImport?.let { info(it) } ?: JSONObject.NULL)
        .put("bf", d.builtInFixtures)
        .put("stats", arr(d.stats) { r ->
            JSONArray().put(r.market).put(r.season).put(r.rank ?: JSONObject.NULL).put(r.team)
                .put(r.value).put(r.games).put(r.percent ?: JSONObject.NULL)
        })
        .put("si", d.statsImport?.let { info(it) } ?: JSONObject.NULL)
        .put("added", arr(d.added) { m -> encMatch(m) })
        .put("ledger", arr(d.ledger) { e -> JSONArray().put(e.fx).put(e.rowKey).put(e.dv).put(e.dg).put(e.tag) })

    private fun <T> arr(list: List<T>, f: (T) -> Any): JSONArray {
        val a = JSONArray()
        for (x in list) a.put(f(x))
        return a
    }

    private fun info(i: ImportInfo) = JSONObject().put("n", i.fileName).put("r", i.rows).put("s", i.skipped).put("t", i.importedAtMillis)

    private fun encodeFixture(fx: FixtureItem): JSONObject {
        val o = JSONObject()
            .put("id", fx.id)
            .put("f", JSONArray().put(fx.fixture.date).put(fx.fixture.time).put(fx.fixture.homeTeam).put(fx.fixture.awayTeam)
                .put(fx.fixture.homeGoals ?: JSONObject.NULL).put(fx.fixture.awayGoals ?: JSONObject.NULL))
            .put("h", fx.home ?: JSONObject.NULL)
            .put("a", fx.away ?: JSONObject.NULL)
        val c = fx.summary?.consensus
        fx.summary?.flags?.takeIf { it.isNotEmpty() }?.let { fl ->
            o.put("kf", arr(fl) { x -> JSONArray().put(x.ruleId).put(x.team).put(x.text) })
        }
        fx.summary?.handicap?.let { o.put("hcp", JSONArray().put(it.label).put(it.confidencePercent)) }
        fx.summary?.over15?.takeIf { it.isNotEmpty() }?.let { v ->
            o.put("o15", arr(v) { x -> JSONArray().put(x.model).put(x.confidence) })
        }
        if (c != null) {
            o.put("models", JSONArray(c.models))
            o.put("picks", arr(c.picks) { p ->
                JSONObject()
                    .put("k", p.key).put("l", p.label).put("g", p.group)
                    .put("b", arr(p.backers) { v -> JSONArray().put(v.model).put(v.confidence) })
                    .put("ag", JSONArray(p.against)).put("ab", JSONArray(p.abstain))
                    .put("mc", p.modelCount).put("st", p.strength)
            })
        }
        return o
    }

    // ---- decoding --------------------------------------------------------------------------

    private fun <T> list(a: JSONArray, f: (Any) -> T): List<T> = (0 until a.length()).map { f(a.get(it)) }

    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }

    private fun encMatch(m: Match): JSONArray {
        val a = JSONArray().put(m.date).put(m.homeTeam).put(m.awayTeam).put(m.homeGoals).put(m.awayGoals)
        val x = m.extras
        if (x != null) a.put(JSONArray()
            .put(x.homeCorners ?: JSONObject.NULL).put(x.awayCorners ?: JSONObject.NULL)
            .put(x.homeCards ?: JSONObject.NULL).put(x.awayCards ?: JSONObject.NULL)
            .put(x.homeReds ?: JSONObject.NULL).put(x.awayReds ?: JSONObject.NULL))
        return a
    }

    private fun match(a: JSONArray): Match {
        val e = a.optJSONArray(5)
        fun n(i: Int): Int? = if (e == null || e.isNull(i)) null else e.getInt(i)
        val x = if (e == null) null else MatchExtras.fromFile(n(0), n(1), null, null, null, null)
            ?.copy(
                homeCards = n(2), awayCards = n(3), cards = if (n(2) != null && n(3) != null) n(2)!! + n(3)!! else null,
                homeReds = if (e != null && e.length() > 5) n(4) else null,
                awayReds = if (e != null && e.length() > 5) n(5) else null
            )
        return Match(a.getString(0), a.getString(1), a.getString(2), a.getInt(3), a.getInt(4), x)
    }

    private fun infoOf(o: JSONObject, key: String): ImportInfo? =
        o.optJSONObject(key)?.let { ImportInfo(it.getString("n"), it.getInt("r"), it.getInt("s"), it.getLong("t")) }

    private fun decode(o: JSONObject): LoadedData {
        val fixtures = list(o.getJSONArray("fixtures")) { x ->
            val f = x as JSONObject
            val fa = f.getJSONArray("f")
            val summary = if (f.has("picks")) {
                FixtureSummary(
                    ConsensusResult(
                        strings(f.getJSONArray("models")),
                        list(f.getJSONArray("picks")) { px ->
                            val p = px as JSONObject
                            ConsensusPick(
                                key = p.getString("k"), label = p.getString("l"), group = p.getString("g"),
                                backers = list(p.getJSONArray("b")) { bx -> (bx as JSONArray).let { ConsensusVote(it.getString(0), it.getDouble(1)) } },
                                against = strings(p.getJSONArray("ag")), abstain = strings(p.getJSONArray("ab")),
                                modelCount = p.getInt("mc"), strength = p.getDouble("st")
                            )
                        }
                    ),
                    f.optJSONArray("hcp")?.let { Prediction(it.getString(0), it.getDouble(1)) },
                    f.optJSONArray("o15")?.let { a -> list(a) { x -> (x as JSONArray).let { ConsensusVote(it.getString(0), it.getDouble(1)) } } }
                        ?: emptyList(),
                    f.optJSONArray("kf")?.let { a -> list(a) { x -> (x as JSONArray).let { KryptoFlag(it.getString(0), it.getString(1), it.getString(2)) } } }
                        ?: emptyList()
                )
            } else null
            FixtureItem(
                f.getInt("id"),
                Fixture(
                    fa.getString(0), fa.getString(1), fa.getString(2), fa.getString(3),
                    if (fa.length() > 5 && !fa.isNull(4)) fa.getInt(4) else null,
                    if (fa.length() > 5 && !fa.isNull(5)) fa.getInt(5) else null
                ),
                if (f.isNull("h")) null else f.getString("h"),
                if (f.isNull("a")) null else f.getString("a"),
                summary
            )
        }
        return LoadedData(
            matches = list(o.getJSONArray("matches")) { match(it as JSONArray) },
            teams = strings(o.getJSONArray("teams")),
            fixtures = fixtures,
            matchesImport = infoOf(o, "mi"),
            fixturesImport = infoOf(o, "fi"),
            builtInFixtures = o.getBoolean("bf"),
            stats = list(o.getJSONArray("stats")) { x ->
                val a = x as JSONArray
                StatRow(
                    a.getString(0), a.getString(1), if (a.isNull(2)) null else a.getInt(2), a.getString(3),
                    a.getDouble(4), a.getInt(5), if (a.isNull(6)) null else a.getDouble(6)
                )
            },
            statsImport = infoOf(o, "si"),
            added = list(o.getJSONArray("added")) { match(it as JSONArray) },
            ledger = list(o.getJSONArray("ledger")) { x ->
                val a = x as JSONArray
                LedgerEntry(a.getString(0), a.getString(1), a.getInt(2), a.getInt(3), a.getString(4))
            }
        )
    }
}
