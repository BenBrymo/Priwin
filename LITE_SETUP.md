# Priwin Picks (lite edition) setup

One project, two apps: `pro` = Priwin, `lite` = Priwin Picks (applicationId com.predictengines.app.lite).
GitHub Actions now builds both: artifacts Priwin-*-apk and PriwinPicks-*-apk.

## Publishing picks to Priwin Picks
1. In Priwin: Data tab > Backup & restore > "Publish data pack (for Priwin Picks)". Save as pack.zip.
   It holds your matches/fixtures, key players, tuning settings (Captain Planet bans, postponed games, Kryptonites, Lokis) and your settled record.
2. Upload pack.zip to a fixed https link, for example the raw link of a file in a GitHub repo
   (https://raw.githubusercontent.com/<you>/<repo>/main/pack.zip) or GitHub Pages. Replace the file each week.
   These hosts send an ETag, so the app only downloads when the file changed.
3. Put that link in app/build.gradle.kts, lite flavor: buildConfigField("String", "PACK_URL", "\"https://...\"").
4. Optional first-launch data: copy a pack.zip to app/src/lite/assets/starter_pack.zip (installed once, before the first download).

Priwin Picks checks the link every time it opens (at most every 30 minutes) and on "Check for updates" (About tab).
A pack older than 7 days shows an out-of-date warning on the Slips and Today tabs.
