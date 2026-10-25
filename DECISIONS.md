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
- **Exact colours drive the rules.**
  - The colour rules have always measured the exact colour (lightness, chroma, hue).
  - The family is used for the pairing table (C7) and the neutralness floor.
  - A typed code recomputes Lab and the family from that code, so typing #3B2A22 changes both
    at once.
- **Family matching weights hue (a, b) twice as much as lightness.** Plain ΔE on very dark
  colours is dominated by lightness, which put a dark brown jacket closer to navy than to
  chocolate.
- **White balance is off by default.** Phone cameras already white-balance. Gray-world over the
  whole photo greys out a photo that is mostly one brown garment. Auto now averages only the
  background (outside the cutout mask), assuming a neutral backdrop; with no mask it uses a
  border frame.
- **Hand edits win.** Once colours are edited by hand, redoing the cutout or white balance no
  longer overwrites them. A hand-set name or formality survives colour edits.
- **Up to 6 colours per piece.** The biggest share is primary, shares of 20 % and up are
  secondary, smaller ones are accents.
- **"Same as…"** copies every tag from the source piece: subcategory, slots, pattern,
  materials, warmth, formality, styles, seasons, fit, layering, shoe and accessory extras,
  laundry threshold, brand, size and tags. The piece keeps its own id, colours, photos,
  status, flags, price, purchase date, notes and wear count.
- **Seasonal swap** uses the current meteorological season. It is shown as a preview first.
  - Available pieces not tagged for the season → Stored.
  - Stored pieces tagged for it → Available.
  - Laundry, tailor, lent and retired pieces are untouched.
- **The shopping list lives in Settings**, so it is included in export and backup with no new
  Room table or migration. The same piece in the same colour is listed once, and adding it
  again refreshes its count.
- **Rotation counts what was shown, not only what was worn.**
  - Most people swipe more than they log wears, so wear-based rotation (R1) alone let the same
    high-scoring pieces win every day.
  - The deck log now stores each card's pieces; older entries hold only signatures and still
    load.
  - Diversification (MMR) subtracts 0.05 for each earlier card in the deck that used the same
    piece.
  - It also subtracts up to 0.25 for a card whose pieces were all in the last 5 decks.
  - This lives in deck building, not in the rule groups, so Match % still means "how good is
    this outfit".
- **Coverage over pure score.** The beam keeps the best partial outfit for every piece, and the
  candidate list keeps the best outfit per core piece next to the top 200.
- **Clashes are data.** `ColorTables.clashes` lists family pairs that read badly even when hue
  harmony likes them. C7 scores them 0.15 with weight 4. The list is deliberately short; anything
  else is left to your swipes and custom rules.
- **"Wear it again"** lists liked outfits:
  - worn at least once;
  - off cooldown (per outfit, or the global setting);
  - with every piece available.
  Longest unworn comes first.

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
