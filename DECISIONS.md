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
  - It also subtracts up to 0.25 for a card whose pieces were in every one of the last 5 days'
    decks. Only earlier days count (all decks of one day count as one), so today's deck stays
    the same when the app is reopened and changes from one day to the next.
  - This lives in deck building, not in the rule groups, so Match % still means "how good is
    this outfit".
- **Fair-share rotation (replaces the flat exposure penalty).** A piece's fair share is how
  often its slot appears, divided by the number of usable pieces for that slot.
  - **In-deck cap:** a piece can appear at most its share rounded up (2 uses for 8 bottoms in a
    12-card deck). When no card fits, the card that breaks the caps least is used.
  - **Swaps:** a card that would break a cap gets the over-used piece swapped for the
    least-used one of its slot, if Match drops by no more than 0.08. The first card is never
    swapped, so it stays the best outfit.
  - **Across days:** pieces shown more than their share in recent decks lose 0.3 per unit of
    overuse; pieces shown less gain up to 0.3.
  - **Turn cards:** 2 cards per deck go to the least-shown tops, bottoms and layers, using their
    best outfit at or above the 30th percentile. They are placed second and fifth and say
    "Giving the … a turn". They are skipped on the first deck, when there is no history yet.
  - **History:** decks of earlier days count; a "More" deck also counts today's decks, so it
    moves on from what you just saw.
  - **The generator keeps the best 4 outfits per core piece** (not 1), so a rarely-used piece
    has more than one card to offer.
  - **Two-week simulation on the demo wardrobe:** every eligible piece is shown, and each lands
    between a third and twice its fair share. Before, the least-shown top got 3 cards and the
    most-shown bottom 53. Mean Match stays at 0.83–0.87.
- **"Why isn't this suggested?"** The item page says which hard constraint blocks a piece today:
  laundry or storage, never suggest, resting after a no, weather, occasion, personal colour, or
  strict rotation.
- **Coverage over pure score.** The beam keeps the best partial outfit for every piece, and the
  candidate list keeps the best outfit per core piece next to the top 200.
- **Clashes are data.** `ColorTables.clashes` lists family pairs that read badly even when hue
  harmony likes them. C7 scores them 0.15 with weight 4. The list is deliberately short; anything
  else is left to your swipes and custom rules.
- **Colour naming.**
  - A colour with chroma ≥ 0.03 is never matched to an achromatic family (black, greys,
    white), so deep navy can't read as black.
  - Denim families are candidates only for pieces made of denim; a dark-blue sweater is navy
    or blue.
  - A "Blue" family (#3A5A9B) fills the gap between navy and cobalt.
  - `ItemFactory.normalizeColors` re-derives families and the automatic name. Stored pieces are
    repaired once per `COLOR_NAMES_VERSION` at app start.
- **Hand-set values win over derived defaults.** `refreshDerived(item, before)` updates formality
  or seasons only if they still matched the automatic value before the edit.
- **Colour shares are normalised to 100 %.** Adding a colour takes its share out of the others,
  and dragging one share rebalances the rest in their old proportions.
- **Collection influence is a bonus, not a rule group.**
  - Similarity to the closest saved or composed outfit is 0.6 × shared colour-family pairs +
    0.4 × shared core pieces (both Jaccard).
  - It adds up to 0.08 raw (about 0.2 Match %) after the groups are combined, like custom soft
    rules, so outfits that aren't similar are not dragged toward the middle.
  - The exact saved outfit gets nothing, so the deck finds new outfits in the same spirit
    instead of repeating saves (cooldowns and "Wear it again" cover repeats).
- **The influence switch also stops saves and compositions from teaching taste**
  (`LearningConfig.learnFromSaved`). Flipping it replays the event log, so it applies to past
  saves too. Swipes, wears and check-ins keep teaching.
- **Label check suggests, never edits.**
  - It covers only tops, mid layers and outerwear, the groups that get mixed up.
  - Signals in order of strength: a typed name (a keyword table), materials, the ML Kit photo
    hint (allowed ML, behind the hints toggle), and slots edited by hand. Each piece gets at most
    one suggestion.
  - A photo saying "jacket" is ignored for pieces that already work as outerwear (overshirt,
    gilet, fleece).
  - A fix re-applies the new subcategory's defaults through `ItemFactory.recategorize`, which
    keeps id, colours, pattern, photos, notes, status, flags, wear count and a typed name. The
    editor's category change now uses the same function.
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

### Speed and battery
- **Deck first, weather second.** Today builds the deck from the cached forecast at once and
  rebuilds only when a newer forecast arrives, instead of waiting for location and network.
- **Location costs nothing most of the time.**
  - A fresh cached forecast (under 3 h old) only checks the last-known location.
  - A network fix is requested only when the cache is stale or on a manual refresh.
  - GPS is never switched on.
  - Reverse geocoding is cached per place.
- **Daily jobs are scheduled with KEEP at app start** and re-scheduled (UPDATE) only when the
  user changes the times. Before, every launch re-enqueued them. The weekly backup waits for
  battery-not-low and storage-not-low.
- **Check labels caches ML Kit hints** per piece and edit time, and decodes photos at half size.
- **Baseline profile** (`app/src/main/baseline-prof.txt`, wildcard rules for our own code) plus
  `androidx.profileinstaller` 1.4.1 (new dependency: the official Jetpack library that
  installs the profile on sideloaded APKs, about 30 KB), so our code is compiled ahead of time
  instead of running in the interpreter first.

### Fresh outfits, experiment mode, colour reading
- **Shown outfits are held back.** The core-piece signatures of outfits from the last 3 days'
  decks (and today's, for "More") are left out of new decks while at least twice the deck size
  of fresh candidates remains; otherwise shown ones fill in at the end. Accessories don't make
  an outfit new.
- **Experiment mode (Today chip, Style profile switch).**
  - Boldness +0.35, Adventurous strictness, at least 40 % new-idea cards.
  - Learned taste weighted ×0.4, and MMR λ −0.15 (more variety).
  - Unusual (non-clashing) pairs are welcome.
  - Known clashes and all hard rules still apply.
- **Item seasons count.** Each core piece not tagged for any season that fits today costs 0.04
  raw (about 0.1 Match). Today's seasons are the calendar season plus summer on summer-like days
  (feels ≥ 22 °C), winter on cold ones (≤ 8 °C), and spring and autumn on mild ones. A soft
  penalty, not a block.
- **"Worn as" is in the main item form.** Unticking "Outer layer" on an overshirt is a choice:
  Check labels flags layers only when the subcategory's main layer is missing.
- **Greek names in Check labels**, matched accent-free with Unicode word boundaries (ζακέτα,
  παλτό, μπουφάν…).
- **The "Why not?" reasons stay** until you pick, skip or swipe on, and wrap so all are visible.
- **GarmentColors** replaces plain k-means for photos:
  - Pixels are taken only from well inside the cutout (fully opaque with opaque neighbours,
    no smoothing when downsizing), so edges blended with the background don't count.
  - Shades of one fabric (same hue at a different lightness, chroma washed out by shadow or
    glare) merge into one colour.
  - Each colour is the median of its pixels after trimming the darkest 25 % and brightest
    10 %.
  - Wardrobe multi-select has "Re-read colours" for pieces added before.
- **"Don't suggest outerwear"** (Today "No jackets" chip, Style profile switch).
  - Structures drop the outer layer, even in the cold, unless a jacket is locked in by hand.
  - A scenario's required jacket is waived.
  - The weather rules still score warmth, so mid layers fill in.
- **Match by eye** (palette icon on each colour, and next to "Add a colour").
  - The whole screen becomes the colour, at full brightness and kept awake, so the phone can be
    held next to the piece.
  - HSV controls follow the finger from the first touch: hue column on the right (37 stops,
    smooth), lightness on the left, saturation along the bottom.
  - Dragging on the colour field fine-tunes it (0.04° hue and 0.0006 value per pixel).
  - Done writes the exact code through the same path as typing it.
- **Layering visibility.**
  - A top's share of the look is 0.03 when both a pullover mid layer and a closed coat cover
    it, the "under closed" share with one of them, and the "under open" share otherwise.
  - Pullover = a mid layer whose Layering.canWearOpen is false.
- **"Don't suggest mid layers"** works like the outerwear switch: structures drop MID unless it
  is locked, and a scenario's required mid layer is waived.
- **Stylist techniques** (StylistRules, data-driven checks on visible pieces).
  - One technique per outfit, the most special first; bonus 0.03 raw (×1.5 in Experiment).
  - The deck gives +0.06 to a technique it hasn't shown yet and −0.06 per repeat beyond two;
    the first card is unaffected.
  - Each repeated upper or bottom colour family costs 0.05, so "blue top, grey bottoms" can't
    fill a deck.
  - The fair-share cap became a cost (0.4 × excess) instead of a filter, so variety rules and
    rotation can trade off instead of emptying the candidate list.
  - Technique names and blurbs are strings.xml resources; the engine passes the enum id.
- **Today layout.** The weather card became a pill in the header and day selection moved to a
  menu on the date, freeing about 200 dp for the card. The accent colour sits only on UI
  (badge, headline tags, Like button), never behind clothing photos.
