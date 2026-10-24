Built-in leagues are detected from the files in this folder, so you can add any number of them without
changing code.

  <id>_matches.csv      played results the models learn from. Format: Date,Time,HomeTeam,AwayTeam,FTHG,FTAG
                        (dates Day/Month/Year). Example: premier_league_matches.csv
  league_names.csv      OPTIONAL. One "id,Display Name" per line, e.g.  mls,Major League Soccer
                        It also sets the order of the league tabs (listed ones first).

Upcoming fixtures for a league go in ../fixtures/<id>_fixtures.csv (optional).

Without a names entry the name is made from the id: premier_league -> Premier League, serieA -> Serie A.
A league exists if it has a matches file or a fixtures file. Files that don't end in _matches.csv are ignored.
