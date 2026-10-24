# Priwin

Native Android app (Kotlin + Jetpack Compose) that runs **three prediction models side by side**
on any fixture, with a modern dark card UI.

| Model | What it does |
|-------|--------------|
| **Brymo** | Tag-based heuristic engine. Tags each team from its record (Wins / Draws / Losses / Balanced, Over 2.5, High/Low scoring, BTTS, +2 handicap) and turns tag matchups into labeled, confidence-scored calls. |
| **Poisson** | Statistical goal model. Attack/defence strength per team (shrunk toward league average), expected goals, a full scoreline grid, then 1X2, double chance, Over/Under 1.5 / 2.5 / 3.5, BTTS, correct-score and fair odds. |

| **Stats** | Reads a stats CSV you load (statschecker-style): Wins / Draws / Lost, Wins to 0, Win or Draw, BTTS, First to Score, Over/Under goals 1.5-3.5, Corners 8.5-11.5 and Cards 3.5-6.5, each Total / Home / Away. Small samples are shrunk toward typical rates, then each team's edge feeds 1X2 evidence and market picks. Shows exactly which stats it used. |

A **consensus** card after the three model cards gives the 1X2 verdict (all agree / majority / no consensus) and the **best picks across the models**: every model is mapped onto shared bets (1X2, double chance, Over/Under 1.5-3.5, BTTS) and the bets backed by at least two models are ranked by agreement and average confidence.

## What's new (latest)

- **Any number of built-in leagues, detected automatically.** Drop `<id>_matches.csv` into
  `app/src/main/assets/leagues/` (and optionally `<id>_fixtures.csv` into `assets/fixtures/`) and the league
  appears as a tab. Names are made from the id (`premier_league` -> Premier League); add a `league_names.csv`
  (`id,Display Name` per line) to choose names and tab order. See the README.txt in each folder.

- **Aliases for every club in the 9 built-in leagues** (`data/ClubAliases.kt`): all 172 teams, with their short
  forms, full official names and the spellings the stats file uses. Tested with 237 alternate spellings, and no
  two clubs in a league merge.
- **Link a name by tapping it**: if a fixture's team isn't recognised, tap **Link** on the fixture card (Not ready
  section) and pick the right team from your matches file. If a team isn't in the stats data, tap **Link** in the
  Stats card and pick the stats team. The link is saved on the phone, applies to every file, and is listed under
  **Data > Name links**, where you can remove it. Nothing needs editing in any file.
- A matches-file date written as `29/09/2025 19:00` in the date column is now read correctly.

## Built-in data and name matching

- **Built-in stats**: the statschecker 2026/2027 file ships inside the app and loads automatically. A stats file
  you load from your phone is added on top of it (your rows win); "Restore built-in" removes your additions.
- **Built-in fixtures slots**: put `<league id>_fixtures.csv` in `app/src/main/assets/fixtures/` (see the README there)
  and that league's fixtures load automatically. A fixtures file loaded from the phone overrides it.
- **One name per club, across all three files**: "Man City" = "Manchester City", "AS Roma" = "Roma",
  "Ath Madrid" = "Atletico de Madrid", and so on. This also works inside one file: a club spelled two ways in the
  matches file becomes one team. Tags like FC / CF / AS / AC, case and accents are ignored. Common nicknames and
  short forms live in `data/ClubAliases.kt`; if two spellings of a club are not linked, add one line there.

## Earlier changes

- **Fixtures show consensus picks only**, in any market (1X2, double chance, goals, BTTS, and more). If no bet is
  backed by two models, the strongest single-model pick is shown and labelled with its model. Tap a fixture for each
  model's full analysis and a **Mark results** card.
- **Played** and **Not ready** fixtures are folded into collapsible sections under the upcoming list.
- **Tick / cross**: mark each prediction correct or wrong (on played fixtures in the list, and per model in the
  detail view). Marks are saved on the phone.
- **Reports tab**: weekly (Monday to Sunday, by kickoff date) correct / wrong / hit rate, split by model and by
  market, with a Share button that sends the report as text.
- **Stats model** always predicts when at least one team has data. Other-venue stats (a Home stat for an away game)
  count at reduced weight. "Win to Nill" spellings and short team names ("Man City") are matched.

## Screens

- **Fixtures**: every game in your loaded fixtures file, grouped by day. Each card shows the Poisson
  1X2 bar, Brymo's top pick, expected goals and Over 2.5. Tap a game for the full breakdown.
- **Matchup**: pick any two teams from the league manually.
- **Reports**: weekly record of your ticked and crossed predictions.
- **Data**: load files from phone storage, manage leagues, see the expected file format.

## Loading your own files from phone storage

Data tab → **Load from phone** → pick a file in the system file picker. No storage permission is
needed (it uses Android's document picker). Files are copied into the app's private storage, so they
keep working even if you move or delete the original.

- **Stats** (Stats model, shared by all leagues): `market,season,rank,team,stat_value,games,percentage` (extra columns like `team_url` are ignored). Loading merges into existing stats, so you can load one export per market; "Remove all" wipes it. The Data tab lists which markets were understood and which ignored.
- **Match results** (what the models learn from): `Date,Time,HomeTeam,AwayTeam,FTHG,FTAG`
- **League fixtures** (what to predict): `Date,Time,HomeTeam,AwayTeam`

**Extra columns are fine.** The matches file only needs `Date`, `Time`, `HomeTeam`, `AwayTeam`, `FTHG`, `FTAG`.
The app finds them by header name wherever they sit (also `Home Team`, `Home_Goals`, etc.), ignores every other
column (odds, shots, referee, half-time goals, xG...), skips title lines above the header, and tells you after
import which columns it used and how many it ignored. Half-time columns are never mistaken for full-time goals.

Accepted: `.csv`, `.tsv`, `.txt`; comma, semicolon or tab separated; header row recommended (column
order then doesn't matter; football-data.co.uk style `FTHG`/`FTAG` works). Dates: matches files use Day/Month/Year
(`21/08/2026`) and so do fixtures files (`19/10/2026 19:00`, time may sit in the Date cell); `yyyy-MM-dd` works in both. Bad rows are skipped and counted; a file with no usable rows is
rejected with an explanation and nothing is overwritten.

Team names in the fixtures file are matched to the matches file ignoring case, accents and suffixes
like FC/CF (`Real Madrid CF` → `Real Madrid`). Names that are genuinely different spellings
(e.g. `Atletico Madrid` vs `Ath Madrid`) are shown in red on the fixture card so you can fix the file.

Each league keeps its own files. Use **Add a league** to create any league, then load its matches and
fixtures. The built-in leagues (any number, detected from `assets/leagues`) come with results data; you can override those with your own file and
restore the built-in data any time.

`sample_data/` has files to try the import with (copy them to your phone first).

## Build & install (Android Studio, or IntelliJ IDEA with the Android plugin)

1. **Open** the `PredictEngines` folder (`File → Open`). Accept the prompt to download Gradle 8.7
   (the wrapper jar isn't included; the IDE fetches Gradle from `gradle-wrapper.properties`).
   Needs internet once. If the IDE doesn't offer it: Settings → Build Tools → Gradle → *Gradle JDK*
   = a JDK 17+, and *Use Gradle from* = `'gradle-wrapper.properties' file`.
2. **Android SDK**: install *Android SDK Platform 34* via SDK Manager (Android Studio does this for you).
3. **Phone**: Settings → About phone → tap *Build number* 7× → Developer options → enable *USB debugging*.
   Plug in via USB and accept the prompt.
4. Pick your phone in the device dropdown, press **Run ▶**.
5. **Standalone APK**: `Build → Build Bundle(s) / APK(s) → Build APK(s)`, then take
   `app/build/outputs/apk/debug/app-debug.apk` to your phone and allow install from unknown sources.

Requirements: Android 8.0+ (API 26). Toolchain: AGP 8.5.2, Kotlin 2.0.21, Gradle 8.7, Compose BOM 2024.06.

## Project layout

```
app/src/main/java/com/predictengines/app/
  MainActivity.kt
  data/        Models, CsvParser, DateUtils, TeamMatcher, LeagueRepository (assets + imported files)
  engine/      BrymoEngine, PoissonEngine, TeamStatsEngine
  viewmodel/   AppViewModel
  ui/          AppRoot (nav), screens/ (Fixtures, Matchup, Data, AnalysisContent), components/, theme/
app/src/main/assets/leagues/   built-in historical results for 9 leagues
sample_data/                   files for testing the phone import
```

## Notes

- Models are statistical/heuristic and the bundled datasets are small (≈7 games per team in some
  leagues), so treat outputs as informational only, not betting advice. Gamble responsibly.
- Poisson needs ≥10 matches in the league and ≥1 for each team; the card shows how many games each
  side's strengths are based on and warns on small samples.

## No IDE? Build the APK online (GitHub Actions)

1. Create a free GitHub account and a new **private** repository.
2. Upload the *contents* of this folder to it (Add file → Upload files). Make sure the
   `.github/workflows/build-apk.yml` file is included. If your browser skipped the hidden `.github`
   folder, use Add file → Create new file, type `.github/workflows/build-apk.yml` as the name and paste
   the file's contents.
3. Open the **Actions** tab → *Build APK* → wait for the green tick (about 5 minutes).
4. Open the finished run, download the **Priwin-debug-apk** artifact, unzip it, and send
   `app-debug.apk` to your phone. Tap it and allow "install from unknown sources".
5. If the run fails, open it and copy the red error lines; they show exactly what to fix.


## Keeping data current (no file editing)

- **Add a score**: on a played fixture, tap *Add score*. It is saved into that league's matches (on the phone, next to
  the built-in file, never counted twice) and the Brymo/Poisson models learn from it immediately. Use *Data > Export matches*
  to get the combined CSV when you want to refresh the bundled file.
- **Stats follow the score**: saving a score adds one game to every stat it settles for both teams (wins, draws,
  losses, win to nil, win or draw, BTTS, goal lines) and one hit where it happened: Man City Total Wins 6/6 becomes 7/7.
  Only each team's latest-season row changes, and teams missing from a market are skipped. Editing or removing a score takes its changes back exactly.
- **Ticks feed the rest**: corners, cards and first-to-score can't be read from a score, so ticking a *Stats* pick for them updates
  the stats (correct = the picked event happened, wrong = its opposite did, e.g. Over 9.5 corners wrong gives the Under 9.5 row the hit).
  Clearing the tick takes it back. Picks a score decides are updated by the score, so they are never counted twice.
- **Past games keep their original prediction**: a played fixture is always shown as predicted from the games and stats known before it.
- **Stats tab**: search, edit a record's value/games (percentage recalculates), add a record, export the file. Records below 50% get a Delete
  button (or *Delete all shown* with the "below 50%" filter); deleted records stay deleted even though the built-in file still has them.
  Ranks are left as they are (the model doesn't use them).
