# Decisions

Choices made where the spec left room, or where it was changed. Newest first.

## 2026-10-24 — Initial build

### Process and tooling (changed from the spec)
- **Cloud build instead of Windows + Android Studio.** The user asked for the app to run on
  their phone with permanent memory and to use GitHub for the code. GitHub Actions builds,
  tests and publishes a signed APK to GitHub Releases (`latest` from main, `preview` from
  other branches). docs/SPEC.md's setup sections were replaced accordingly.
- **Repository repurposed.** The previous computer-vision exercises were removed (still in git
  history before this change).
- **Fixed signing key committed** (`app/keystore/ensemble.jks`, password in app/build.gradle.kts).
  A stable key is what lets each new APK install as an update and keep the wardrobe. It is a
  personal, sideloaded app, so a committed key is the simple option; before a Play Store release,
  move to Play App Signing with an upload key kept as a GitHub secret.
- **versionCode = CI run number**, so every build is an update.
- **Application ID** `io.github.aristidisg.ensemble` (never change it after installing).

### Permanent memory
- Room + DataStore + files in app-private storage; Android Auto Backup on for database, files
  and settings (cloud backup and device transfer); export/import zip; weekly automatic backup
  into a user-picked folder via the Storage Access Framework (works with Google Drive).

### Stack versions
- AGP 8.10.1, Kotlin 2.1.21 (K2), KSP 2.1.21-2.0.2, Compose BOM 2025.05.00, Hilt 2.56.2,
  Room 2.7.1, Navigation 2.9.0 (type-safe routes), Coil 3.1.0, OkHttp 4.12.0, WorkManager
  2.10.1, Glance 1.1.1, ML Kit subject segmentation 16.0.0-beta1, image labeling 17.0.9.
  Chosen as a combination known to work together rather than the newest of each.
- **Network:** OkHttp + kotlinx.serialization (no Retrofit; two endpoints only).
- **Charts:** plain Compose Canvas (palette bar, coverage heatmap). No chart library.
- **Tests:** JUnit 5 + kotlin.test in :engine; property tests are seeded loops over random
  contexts and wardrobes (no Kotest), keeping the engine's test classpath tiny.

### Data
- **Room tables hold JSON documents** plus the columns we query (items, outfits,
  feedback_events, preference_stats, wear_logs, check_ins, day_plans, trips, inspiration_looks,
  custom_rules, scenarios, weather_cache, deck_log). The spec's child tables (item_colors,
  item_photos, outfit_items, trip_items, look_pieces) live inside their parent documents.
  Fewer migrations while the model settles; still Room with exported schemas.
- **No persisted pair cache.** Computing all pair scores for 300 items takes milliseconds,
  so the generator builds it in memory per run.
- **Scenario presets** come from the engine tables; edits and custom scenarios are stored and
  overlay them. Deleting an edited preset restores the default.

### Engine
- **Match % calibration.** Group means land between ~0.6 and 1.0 in practice, which made
  every outfit read 75–99 %. Match % = (raw − 0.6) / 0.4, clamped. Golden case 1 still scores
  ≥ 85; thresholds such as "good outfit ≥ 70" apply to the calibrated value.
- **Value contrast (C4):** contrast above the personal band is a milder fault than below it
  (a white shirt with navy trousers should not be penalized like grey mush).
- **Pattern hint** requires both contrasting 20 %+ clusters *and* dense edges.
- **Accessories pass runs on the chosen cards** (after MMR) and only fully rescores the top 3
  candidates per accessory slot by pair score. Keeps a 300-item deck at ~0.3 s on a desktop JVM.
- **Items carry their allowed slots** (overshirt: mid or outer; hoodie: mid or top) and outfit
  pieces record the slot used.
- **Outer worn open/closed** is derived: warm coats, pieces that can't be worn open, or
  feels-like < 12 °C mean closed.
- **"Forget this"** removes a feature from the cached stats; "Rebuild from history" replays the
  log and would bring it back (the log is the source of truth).
- **Seasons** are northern-hemisphere meteorological seasons.

### App
- **Camera:** the system camera via `TakePicture` instead of an in-app CameraX screen (no extra
  dependency, works on every phone). Photo Picker for gallery multi-select, share sheet for
  browser/Instagram images.
- **Location:** `LocationManager` coarse location (no Play Services location dependency);
  coordinates rounded to 0.01° before they leave the phone; saved-city fallback.
- **Explanations:** the Appendix C4 templates are in strings.xml; other keys fall back to the
  engine's English templates.
- **"Seed demo wardrobe"** is available in release builds too (empty state and You → debug), so
  the app can be tried before photographing anything.
