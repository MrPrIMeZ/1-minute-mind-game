# 1 Minute Mind Game 🧠⏱️

A native Android brain-training game (Kotlin + Jetpack Compose): 10 one-minute games, a daily
challenge with streaks, XP/levels, 18 achievements, a Brain Score across 5 skills, and AdMob ads.

## Games
Quick Math · True or False · Which Is Bigger · Colour Clash · Memory Grid · Odd One Out ·
Next Number · Number Rush · Match Back · Arrow Focus. All questions are generated, so they never run out,
and every game gets harder the better you play.

## Ads (AdMob)
- Banner on menu/result screens only (never during a game)
- Interstitial after every 2nd practice game, at most once per 90 seconds, never during the daily challenge
- Rewarded (always optional): +15 seconds, double XP, save a broken streak
- Google UMP consent form for EU/UK players
- IDs live in `gradle.properties`. They are Google's **test IDs** until you replace them with your own and set `ADS_TESTING=false`.

## Build
Every push to `main` builds on GitHub Actions:
- **Test APK** → Actions → latest run → *Artifacts* → `1-minute-mind-test-apk`
- **Play Store AAB** is built too once these repo secrets exist: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

Locally: open the folder in Android Studio, or run `./gradlew assembleDebug`.

## Going live checklist
1. AdMob: create the app + 3 ad units (banner, interstitial, rewarded), put the IDs in `gradle.properties`, set `ADS_TESTING=false`, and add `app-ads.txt` details.
2. Privacy policy: `docs/privacy-policy.html` is served by GitHub Pages at
   https://mrprimez.github.io/1-minute-mind-game/privacy-policy.html (fill in the contact email).
3. Play Console: create the app, fill in the store listing from `store/store-listing.md`, and upload the AAB.

The signing key is **never** committed (see `.gitignore`).
