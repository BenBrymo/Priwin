Built-in fixtures: one file per league, named <league id>_fixtures.csv, for example la_liga_fixtures.csv.
The league id is the start of the league's matches file name in ../leagues (la_liga_matches.csv -> la_liga).
Format: Date,HomeTeam,AwayTeam - Date as dd/MM/yyyy HH:mm (time in the same cell, or a separate Time column). Extra columns are ignored.
A league that has only a fixtures file still appears in the app; you can load its results from the phone.
A fixtures file loaded from the phone always overrides the built-in one (and can be removed to go back to it).
