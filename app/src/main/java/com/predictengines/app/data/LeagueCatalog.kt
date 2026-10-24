package com.predictengines.app.data

import java.util.Locale

/**
 * Works out which built-in leagues exist from the file names in the app's assets, so adding a league is
 * just dropping files in a folder (no code change, any number of leagues):
 *
 *   assets/leagues/<id>_matches.csv     results the models learn from
 *   assets/fixtures/<id>_fixtures.csv   upcoming games to predict (optional)
 *   assets/leagues/league_names.csv     optional: `id,Display Name` per line; also sets the order
 *
 * A league exists if it has a matches file or a fixtures file. Without a names entry the display name is
 * made from the id ("premier_league" -> "Premier League", "serieA" -> "Serie A").
 */
object LeagueCatalog {

    const val MATCHES_SUFFIX = "_matches.csv"
    const val FIXTURES_SUFFIX = "_fixtures.csv"
    const val NAMES_FILE = "league_names.csv"

    /** Display names (and default order) for the leagues the app has always shipped with. */
    private val KNOWN: List<Pair<String, String>> = listOf(
        "la_liga" to "La Liga",
        "serieA" to "Serie A",
        "serieB" to "Serie B",
        "championship" to "Championship",
        "eredivisie" to "Eredivisie",
        "eliteserien" to "Eliteserien",
        "league1" to "Ligue 1",
        "portugalpremier" to "Primeira Liga",
        "turkey_superleague" to "Süper Lig"
    )

    private val ACRONYMS = setOf("mls", "epl", "usa", "uk", "usl", "efl", "spl", "uae", "sa", "nb")

    /**
     * @param matchFiles file names found in assets/leagues
     * @param fixtureFiles file names found in assets/fixtures
     * @param namesCsv contents of league_names.csv, if the file exists
     */
    fun detect(matchFiles: List<String>, fixtureFiles: List<String>, namesCsv: String? = null): List<League> {
        val matchIds = matchFiles.filter { it.endsWith(MATCHES_SUFFIX, ignoreCase = true) }
            .map { it.dropLast(MATCHES_SUFFIX.length) }
        val fixtureIds = fixtureFiles.filter { it.endsWith(FIXTURES_SUFFIX, ignoreCase = true) }
            .map { it.dropLast(FIXTURES_SUFFIX.length) }
        val ids = (matchIds + fixtureIds).filter { it.isNotBlank() && !it.startsWith("custom_") }.distinct()

        val given = parseNames(namesCsv)
        val known = KNOWN.toMap()

        fun nameOf(id: String) = given[id] ?: known[id] ?: prettyName(id)

        // Order: league_names.csv first (in its order), then the classic nine, then the rest A-Z.
        val ordered = ArrayList<String>()
        given.keys.filter { it in ids }.forEach { ordered.add(it) }
        KNOWN.map { it.first }.filter { it in ids && it !in ordered }.forEach { ordered.add(it) }
        ids.filter { it !in ordered }.sortedBy { nameOf(it).lowercase(Locale.ROOT) }.forEach { ordered.add(it) }

        return ordered.map { id ->
            League(
                id = id,
                name = nameOf(id),
                assetFile = if (id in matchIds) "leagues/$id$MATCHES_SUFFIX" else null,
                isCustom = false
            )
        }
    }

    /** `id,Display Name` per line (a header row `id,name` is fine). */
    fun parseNames(csv: String?): Map<String, String> {
        if (csv.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (raw in csv.removePrefix("﻿").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val cut = line.indexOf(',').takeIf { it > 0 } ?: line.indexOf(';').takeIf { it > 0 } ?: continue
            val id = line.substring(0, cut).trim()
            val name = line.substring(cut + 1).trim().trim('"').trim()
            if (id.isEmpty() || name.isEmpty() || id.equals("id", ignoreCase = true)) continue
            out[id] = name
        }
        return out
    }

    /** "premier_league" -> "Premier League", "serieA" -> "Serie A", "mls_2026" -> "MLS 2026". */
    fun prettyName(id: String): String {
        val spaced = id
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Za-z])(\\d)"), "$1 $2")
            .replace(Regex("[_\\-.]+"), " ")
            .trim()
        return spaced.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { word ->
            if (word.lowercase(Locale.ROOT) in ACRONYMS) word.uppercase(Locale.ROOT)
            else word.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }.let { w ->
                // keep a lone trailing capital as typed: "serieA" -> "Serie A"
                if (word.length == 1) word.uppercase(Locale.ROOT) else w
            }
        }
    }
}
