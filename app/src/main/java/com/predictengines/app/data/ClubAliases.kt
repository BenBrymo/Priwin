package com.predictengines.app.data

/**
 * Names that mean the same club. Each line is one club, names separated by "|". Case, accents and tags
 * like FC / CF / AS / AC / SK / FK are ignored when comparing, so "AS Roma" and "Roma" would match without
 * an entry; the lines below add what generic matching can't know: nicknames, short forms used by
 * football-data style files ("Ath Madrid", "Sheffield Weds"), the spellings the stats file uses
 * ("Man City", "Atletico de Madrid") and full official names.
 *
 * Every club in the nine built-in leagues has a line. A name must appear in ONE line only: two lines
 * sharing a name would merge two clubs (the unit tests check this). Users can also link names
 * from inside the app, see [NameLink]; those are added on top of these lines.
 */
object ClubAliases {

    val GROUPS: List<List<String>> = """
        # ===== La Liga =====
        Alaves|Deportivo Alaves|CD Alaves|D Alaves
        Athletic Bilbao|Athletic Club|Ath Bilbao|Athletic Club Bilbao|Athletic
        Atletico Madrid|Atletico de Madrid|Ath Madrid|Club Atletico de Madrid|Atl Madrid
        Barcelona|FC Barcelona|Barca|Futbol Club Barcelona
        Real Betis|Betis|Real Betis Balompie
        Celta Vigo|Celta de Vigo|Celta|RC Celta|RC Celta de Vigo
        Elche|Elche CF
        Espanyol|RCD Espanyol|Espanol|RCD Espanyol de Barcelona
        Getafe|Getafe CF
        Girona|Girona FC
        Levante|Levante UD|UD Levante
        Mallorca|Real Mallorca|RCD Mallorca
        Osasuna|CA Osasuna|Club Atletico Osasuna
        Real Oviedo|Oviedo
        Real Madrid|Real Madrid CF
        Sevilla|Sevilla FC
        Real Sociedad|Sociedad|Real Sociedad de Futbol
        Valencia|Valencia CF
        Rayo Vallecano|Vallecano|Rayo
        Villarreal|Villarreal CF
        Real Valladolid|Valladolid
        Sporting Gijon|Sp Gijon
        SD Eibar|Eibar

        # ===== Serie A =====
        Atalanta|Atalanta BC
        Bologna|Bologna FC
        Cagliari|Cagliari Calcio
        Como|Como 1907
        Cremonese|US Cremonese
        Fiorentina|ACF Fiorentina
        Genoa|Genoa CFC
        Internazionale|Inter|Inter Milan|Internazionale Milano|FC Internazionale Milano|Inter Milano
        Juventus|Juventus FC|Juve
        Lazio|SS Lazio
        Lecce|US Lecce
        AC Milan|Milan
        Napoli|SSC Napoli
        Parma|Parma Calcio|Parma Calcio 1913
        Pisa|Pisa SC|AC Pisa
        AS Roma|Roma
        Sassuolo|US Sassuolo|Sassuolo Calcio
        Torino|Torino FC
        Udinese|Udinese Calcio
        Hellas Verona|Verona|Hellas Verona FC

        # ===== Serie B =====
        Avellino|US Avellino
        Bari|SSC Bari
        Carrarese|Carrarese Calcio
        Catanzaro|US Catanzaro
        Cesena|Cesena FC
        Empoli|Empoli FC
        Frosinone|Frosinone Calcio
        Juve Stabia|SS Juve Stabia
        Mantova|Mantova 1911|Mantua
        Modena|Modena FC
        Monza|AC Monza
        Padova|Calcio Padova|Padua
        Palermo|Palermo FC
        Pescara|Delfino Pescara|Delfino Pescara 1936
        Reggiana|AC Reggiana|Reggiana 1919
        Sampdoria|UC Sampdoria
        Spezia|Spezia Calcio
        Sudtirol|FC Sudtirol|Sud Tirol|Suedtirol|South Tyrol
        Venezia|Venezia FC
        Virtus Entella|Entella

        # ===== Ligue 1 =====
        Angers|Angers SCO|SCO Angers
        Auxerre|AJ Auxerre
        Brest|Stade Brestois|Stade Brestois 29
        Le Havre|Le Havre AC|HAC
        Lens|RC Lens
        Lille|LOSC Lille|LOSC|Lille OSC
        Lorient|FC Lorient
        Olympique Lyonnais|Lyon|OL|Olympique Lyon
        Olympique Marseille|Marseille|Olympique de Marseille|OM
        Metz|FC Metz
        Monaco|AS Monaco
        Nantes|FC Nantes
        Nice|OGC Nice
        Paris FC|Paris
        Paris Saint Germain|Paris Saint-Germain|PSG|Paris SG|Paris St Germain
        Rennes|Stade Rennais|Stade Rennais FC
        Strasbourg|RC Strasbourg|RC Strasbourg Alsace
        Toulouse|Toulouse FC
        Saint-Etienne|St Etienne|AS Saint-Etienne
        Montpellier|Montpellier HSC

        # ===== Primeira Liga =====
        AVS|AVS Futebol SAD|AVS SAD
        Alverca|FC Alverca
        Arouca|FC Arouca
        Benfica|SL Benfica|Sport Lisboa e Benfica
        Braga|SC Braga|Sporting Braga|Sporting de Braga
        Casa Pia|Casa Pia AC
        Estoril|Estoril Praia|GD Estoril Praia
        Estrela|Estrela Amadora|CF Estrela da Amadora|Estrela da Amadora
        Famalicao|FC Famalicao
        Gil Vicente|Gil Vicente FC
        Vitoria Guimaraes|Guimaraes|Vitoria SC|Vitoria de Guimaraes|Vitoria Guimaraes SC
        Moreirense|Moreirense FC
        Nacional|CD Nacional|Nacional Madeira|CD Nacional da Madeira
        Porto|FC Porto
        Rio Ave|Rio Ave FC
        Santa Clara|CD Santa Clara
        Sporting CP|Sporting|Sporting Lisbon|Sporting Lisboa|Sporting Clube de Portugal
        Tondela|CD Tondela
        Maritimo|CS Maritimo

        # ===== Super Lig =====
        Alanyaspor|Corendon Alanyaspor|Alanya
        Antalyaspor|Antalya|Bitexen Antalyaspor
        Istanbul Basaksehir|Basaksehir|Istanbul Basaksehir FK|RAMS Basaksehir|Medipol Basaksehir
        Besiktas|Besiktas JK|Besiktas Istanbul
        Eyupspor|Eyup
        Fenerbahce|Fenerbahce SK|Fenerbahce Istanbul
        Galatasaray|Galatasaray SK|Galatasaray Istanbul
        Gaziantep|Gaziantep FK
        Genclerbirligi|Genclerbirligi SK
        Goztep|Goztepe|Goztep SK
        Karagumruk|Fatih Karagumruk
        Kasimpasa|Kasimpasa SK
        Kayserispor|Kayseri|Kayserispor FK
        Kocaelispor|Kocaeli
        Konyaspor|Konya|Tumosan Konyaspor
        Rizespor|Caykur Rizespor|Rize
        Samsunspor|Samsun
        Trabzonspor|Trabzon

        # ===== Eredivisie =====
        AZ Alkmaar|AZ
        Ajax|Ajax Amsterdam|AFC Ajax
        Excelsior|Excelsior Rotterdam|SBV Excelsior
        Feyenoord|Feyenoord Rotterdam
        Fortuna Sittard|For Sittard
        Go Ahead Eagles|GA Eagles|Go Ahead Eagles Deventer
        Groningen|FC Groningen
        Heerenveen|SC Heerenveen
        Heracles|Heracles Almelo
        NAC Breda|NAC
        Nijmegen|NEC Nijmegen|NEC
        PSV|PSV Eindhoven
        Sparta Rotterdam|Sparta
        Telstar|SC Telstar
        Twente|FC Twente|FC Twente Enschede
        Utrecht|FC Utrecht
        Volendam|FC Volendam
        Zwolle|PEC Zwolle
        Vitesse|Vitesse Arnhem

        # ===== Eliteserien =====
        Bodo/Glimt|Bodo Glimt|FK Bodo/Glimt|Bodoe/Glimt
        Brann|SK Brann
        Bryne|Bryne FK
        Fredrikstad|Fredrikstad FK
        HamKam|Hamarkameratene|Ham-Kam|HamKam Fotball
        Haugesund|FK Haugesund
        KFUM Oslo|KFUM|KFUM-Kameratene Oslo|KFUM Kameratene Oslo
        Kristiansund|Kristiansund BK
        Molde|Molde FK
        Rosenborg|Rosenborg BK
        Sandefjord|Sandefjord Fotball
        Sarpsborg 08|Sarpsborg|Sarpsborg 08 FF
        Stromsgodset|Stromsgodset IF
        Tromso|Tromso IL
        Valerenga|Vaalerenga|Valerenga IF|Valerenga Fotball
        Viking|Viking FK|Viking Stavanger
        Lillestrom|Lillestrom SK

        # ===== Championship =====
        Birmingham City|Birmingham|Birmingham City FC
        Blackburn Rovers|Blackburn
        Bristol City|Bristol
        Charlton Athletic|Charlton
        Coventry City|Coventry
        Derby County|Derby
        Hull City|Hull
        Ipswich Town|Ipswich
        Leicester City|Leicester
        Middlesbrough|Boro
        Millwall|Millwall FC
        Norwich City|Norwich
        Oxford United|Oxford|Oxford Utd
        Portsmouth|Pompey
        Preston North End|Preston|Preston NE|PNE
        Queens Park Rangers|QPR
        Sheffield United|Sheffield Utd|Sheff Utd|Sheff United
        Sheffield Wednesday|Sheffield Weds|Sheff Weds|Sheff Wed
        Southampton|Saints
        Stoke City|Stoke
        Swansea City|Swansea
        Watford|Watford FC
        West Bromwich Albion|West Brom|WBA
        Wrexham|Wrexham AFC
        Cardiff City|Cardiff
        Plymouth Argyle|Plymouth
        Luton Town|Luton
        Wigan Athletic|Wigan
        Milton Keynes Dons|MK Dons

        # ===== Premier League =====
        Arsenal|Arsenal FC
        Aston Villa|Villa
        Bournemouth|AFC Bournemouth
        Brentford|Brentford FC
        Brighton and Hove Albion|Brighton|Brighton & Hove Albion|Brighton Hove Albion
        Burnley|Burnley FC
        Chelsea|Chelsea FC
        Crystal Palace|Palace
        Everton|Everton FC
        Fulham|Fulham FC
        Leeds United|Leeds
        Liverpool|Liverpool FC
        Manchester City|Man City|Man. City
        Manchester United|Man United|Man Utd|Man. United
        Newcastle United|Newcastle|Newcastle Utd
        Nottingham Forest|Nott'm Forest|Nottm Forest|Notts Forest
        Sunderland|Sunderland AFC
        Tottenham Hotspur|Tottenham|Spurs
        West Ham United|West Ham|West Ham Utd
        Wolverhampton Wanderers|Wolves|Wolverhampton

        # ===== Germany =====
        Bayern Munich|Bayern Munchen|FC Bayern Munchen|Bayern|FC Bayern Munich
        Borussia Monchengladbach|Monchengladbach|M'gladbach|M Gladbach|Gladbach|Borussia M'gladbach
        Borussia Dortmund|Dortmund|BVB
        Bayer Leverkusen|Bayer 04 Leverkusen|Leverkusen
        Eintracht Frankfurt|Ein Frankfurt|Frankfurt
        Mainz 05|FSV Mainz 05|Mainz
        TSG Hoffenheim|Hoffenheim|TSG 1899 Hoffenheim
        FC Koln|Koln|Cologne|1 FC Koln
        RB Leipzig|Leipzig
        Union Berlin|FC Union Berlin
        VfB Stuttgart|Stuttgart
        VfL Wolfsburg|Wolfsburg
        VfL Bochum|VfL Bochum 1848|Bochum
        Werder Bremen|Bremen
        St Pauli|FC St. Pauli
        Hamburger SV|Hamburg|HSV
        FC Augsburg|Augsburg
        Schalke 04|Schalke
        Holstein Kiel|Kiel
        Darmstadt 98|Darmstadt
        Eintracht Braunschweig|Braunschweig
    """.trimIndent()
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line -> line.split('|').map { it.trim() }.filter { it.isNotEmpty() } }
}
