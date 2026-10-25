# Ensemble — Outfit App Build Spec

Ensemble (working title) is a menswear outfit app that suggests looks from photos of your own
clothes using only rules, tags and counted swipes. This file is the product spec and the source
of truth: when a decision changes, edit it here and log the change in `DECISIONS.md`.

> **Changed from the original spec (Oct 2026):** the original asked for a Windows 11 + Android
> Studio setup and a twelve-session build with the phone plugged in over USB. This repository is
> built in the cloud instead. The sections below replace "How to use this doc", "Setup on
> Windows 11", "Kick-off prompt" and the original CLAUDE.md block (now `/CLAUDE.md`). Everything
> from "Decisions locked in" onward is the original spec text, unchanged.

## How it is built and installed (replaces the Windows setup)

1. **No local toolchain needed.** All code lives in this GitHub repository. GitHub Actions
   (`.github/workflows/android.yml`) runs the engine tests, unit tests and lint, then builds a
   minified, signed release APK on every push.
2. **Install on your phone from GitHub.** Each build is published to GitHub Releases:
   - `latest` — builds from `main`
   - `preview` — builds from any other branch (pre-release)

   On the phone, open the repository's *Releases* page, download `ensemble.apk` and open it.
   Android asks once to allow installs from your browser ("Install unknown apps"). Later builds
   install as updates over the old one.
3. **Permanent memory.** Everything stays on the phone and survives restarts and updates:
   - Room database (`ensemble.db`), DataStore settings and photos live in the app's private
     storage; they are only removed if you uninstall the app or clear its data.
   - Every build is signed with the same key (`app/keystore/ensemble.jks`) and the version code
     is the CI run number, so each new APK installs as an update and keeps your data.
   - Android Auto Backup is enabled (database, settings, photos) so a new phone or a reinstall
     restores from your Google account backup.
   - **You → Your data**: export a `.zip` (manifest, data and images) any time, import it with a
     preview (merge or replace), and switch on a *weekly automatic backup* into any folder you
     pick, including a Google Drive folder.
4. **Working with Claude Code.** Start a Claude Code session on this repository (web, desktop or
   CLI), ask for a change, and it commits to a branch; the `preview` release appears a few
   minutes later. Merge to `main` for `latest`.

## Build status by phase

The twelve phase prompts at the end of this spec are kept for reference; all twelve have a first
implementation in this repository. `PROGRESS.md` tracks what is done, what is simplified and what
is next.

## Decisions locked in

```text
These answers from our Q&A shape every section below; if you change one, change it
here first.

   Area                     Decision

   Audience                 Built for you first, structured for a Play Store
                            release later

   Wardrobe                 Menswear taxonomy, stored as data so it can grow

   Recommendations          100% deterministic rules, tags and counted
                            feedback; no LLMs, no ML models, no cloud AI

   Photos                   On-device ML Kit for background cutouts, plus
                            optional category hints you always confirm (toggle)

   Area                Decision

   Personal color      Optional module with an on/off switch and a
                       strength slider: color season, favorite and avoid
                       colors

   Feedback            Swipe yes/no, "why not?" chips, swipe up = wearing
                       it, rate single pieces, evening check-in

   Liked outfits       Compose from your own clothes, plus inspiration
                       photos the app recreates from your wardrobe

   Fit                 Simple silhouette per item: slim, regular, relaxed,
                       oversized

   v1 modules          Weekly planner, laundry and wear log, trip packing,
                       gap finder, rotation, per-outfit cooldowns

   Shoes               A full outfit slot with dedicated rules

   Accessories         Full set: belt, watch, bag, cap/beanie, sunglasses,
                       scarf, jewelry

   Starting styles     Minimal/clean, smart casual/classic,
                       streetwear/sporty

   Weather             GPS location + Open-Meteo forecast, manual
                       override anytime

   Units               °C, km/h, Monday-first weeks
```

## SPEC 1 — Product, stack and architecture

```text
Ensemble turns photos of your own clothes into outfit suggestions for any scenario and
forecast, explains each one with real style rules, and learns your taste from swipes — with
zero AI.

Core loop. Add a piece (photo → cutout → auto colors → smart-default tags) → pick a day
and scenario → get a deck of outfits → swipe → wear → evening check-in → better decks.

Principles.

     Transparent: every suggestion shows why it works and its trade-offs.
     Controllable: every rule can be switched off or re-weighted; learned taste can be
     inspected and reset.
     Fast: a deck of 12 in under 300 ms for a 300-piece wardrobe.
     Private: everything stays on the phone; only coordinates go to the weather API.

Stack (latest stable versions at build time, pinned in the version catalog):

   Concern                      Choice

   Language, UI                 Kotlin (K2 compiler), Jetpack Compose, Material 3

   Navigation                   Type-safe Navigation Compose, or Navigation 3 if
                                stable — Claude Code checks and logs the choice

   Dependency injection         Hilt + KSP

   Storage                      Room (KSP, exported schemas), DataStore

   Concern                          Choice

   Serialization                    kotlinx.serialization

   Network                          Ktor client or Retrofit + OkHttp (pick one, log it)

   Images                           Coil 3; CameraX for capture; Android Photo Picker
                                    for the gallery (no storage permission)

   On-device ML (photos only)       ML Kit Subject Segmentation (still beta; model
                                    downloaded by Play services; about 200 KB of app
                                    size) and ML Kit Image Labeling for category hints

   Background work                  WorkManager

   Home-screen widget               Jetpack Glance

   Charts                           Vico or plain Compose Canvas

   Tests                            JUnit, Kotest property tests (engine), Turbine,
                                    Robolectric or instrumented Room tests, Compose
                                    UI tests

   SDK levels                       minSdk 26; target and compile SDK = latest stable

Modules.

       :core:model — pure Kotlin data classes, enums and IDs shared by everything.

       :engine — pure Kotlin/JVM: color science, taxonomy defaults, rules, scoring,
     generator, learning, planner, packing, gap finder. No Android, no I/O, no clock, seeded
     RNG only.
       :data — Room entities and DAOs, DataStore, repositories, Open-Meteo client, image
     file storage, export/import.
       :app — Compose UI, ViewModels, Hilt graph, photo pipeline (CameraX, ML Kit, white
     balance), WorkManager jobs, notifications, Glance widget.

Dependency rule: app → data, engine, model ; data → model ; engine → model . The
engine only sees plain model objects that the repositories hand it, which keeps it testable
on the JVM in seconds.

Engine API sketch (names may change; the shape matters):

   class OutfitEngine(config: EngineConfig, tables: StyleTables) {
       fun generate(req: SuggestionRequest, wardrobe: List<Item>, prefs:
   PreferenceSnapshot,
                    history: WearHistory, rng: SeededRandom): SuggestionDeck
       fun score(outfit: Outfit, ctx: EngineContext): ScoredOutfit                          //
   "Rate my outfit"
       fun complete(partial: PartialOutfit, ctx: EngineContext, n: Int):
   List<ScoredOutfit>
       fun alternatives(outfit: Outfit, slot: Slot, ctx: EngineContext, n: Int):
   List<ScoredItem>
       fun recreate(look: InspirationLook, wardrobe: List<Item>, ctx:
   EngineContext): LookMatch
       fun planWeek(days: List<DayContext>, wardrobe: List<Item>, prefs:
   PreferenceSnapshot,
                    history: WearHistory): WeekPlan
       fun packTrip(trip: TripRequest, wardrobe: List<Item>, prefs:
   PreferenceSnapshot): PackingPlan
       fun findGaps(wardrobe: List<Item>, usage: UsageProfile,
                     archetypes: List<Archetype>): List<GapSuggestion>
   }

   object Learning {
       fun apply(event: FeedbackEvent, stats: PreferenceStats, cfg:
   LearningConfig): PreferenceStats
       fun rebuild(events: List<FeedbackEvent>, cfg: LearningConfig):
   PreferenceStats
   }
```

## SPEC 2 — Data model

```text
Every piece of clothing is an Item whose tags arrive pre-filled from its subcategory
(Appendix A), so you only correct what's wrong.

Item — the core record:

   Field             Type / values                        Notes

   id                UUID

   name              String                               Auto ("Navy oxford shirt"),
                                                          editable

   subcategory       enum (Appendix A)                    Decides slot, layer and every
                                                          default

   Field                Type / values                          Notes

   colors               1–4 ItemColor                          Hex, OKLab, family, share %,
                                                               role (primary / secondary /
                                                               accent)

   pattern              type + scale + loudness 0–3            Solid, stripes fine/bold, check
                                                               small/large, herringbone,
                                                               houndstooth, floral, graphic,
                                                               logo small/large, camo, texture-
                                                               only

   materials            list                                   Cotton, linen, wool, merino,
                                                               cashmere, denim, leather, suede,
                                                               corduroy, flannel, tweed,
                                                               nylon/tech, fleece, knit, jersey,
                                                               canvas

   fabricWeight         light / mid / heavy

   warmthClo            Float                                  Default from subcategory +
                                                               material (Appendix A)

   waterResistance      none / resistant / waterproof

   formality            1–10                                   Default from subcategory; logos
                                                               −1, dark solid tailoring +1

   styleTags            set                                    Minimal, smart casual, classic,
                                                               streetwear, sporty, rugged,
                                                               outdoor, preppy, resort,
                                                               techwear, plus custom

   seasons              set                                    Derived from warmth and
                                                               material, editable

   silhouette           slim / regular / relaxed / oversized   The only fit field you set

   layering (derived)   canWearOpen, presentableAlone,         From subcategory; editable
                        bulk 1–3                               under "advanced"

   shoe extras          profile (sleek / standard / chunky),   Footwear only
                        upper (leather / suede / canvas /
                        knit / synthetic / rubber), sole
                        (leather / rubber / lug),
                        weatherproof, restDays

   Field                Type / values                          Notes

   accessory extras     leatherColor (belt, shoes, bag,
                        strap), metalTone (gold / silver /
                        gunmetal / rose / black)

   laundry              wearsBeforeWash,                       Defaults by subcategory
                        wearsSinceWash

   status               available / in laundry / at tailor /   Only "available" gets suggested
                        stored / lent / retired

   flags                favorite, signature, neverSuggest

   meta                 brand, size, price, purchaseDate,      Price powers cost per wear
                        notes, customTags

   photos               original, cutout, thumbnail paths

   derived (not         wearCount, lastWorn,                   Computed from WearLog
   stored)              daysSinceWorn, costPerWear

Other records.

     Outfit — id; signature = hash of sorted item IDs (the dedupe key); items by slot;
     source (generated / composed / recreated / planned); name; scenarioId; liked; rating;
     optional collage layout; cooldownDays override; notes. CooldownUntil, timesWorn and
     lastWorn are derived from WearLog.
     Scenario — name, icon, formality min/target/max, time window, indoor share 0–1,
     activity (sedentary / light / active), required slots, forbidden subcategories or tags,
     preferred style weights, palette bias, accessory budget. Presets in Appendix C.
     FeedbackEvent (append-only log) — timestamp, type, outfit signature, item IDs, target
     item, reasons, check-in payload, context snapshot (scenario, season, temperature
     band, weekday, feels-like range), extracted feature keys and the weights applied.
     Learned stats can always be rebuilt from this log.
     PreferenceStat — featureKey, contextKey, pos, neg, lastUpdatedEpochDay. Decay is
     applied when read.
     WearLog — date, outfit signature, item IDs, scenario, weather snapshot. Drives
     rotation, cooldowns, laundry counts and cost per wear.
     CheckIn — date, comfort 1–5, thermal (too cold / ok / too warm), felt 1–5, note.
     DayPlan — date, scenarioId, outfit signature, locked, note.
     Trip — destination (name, lat, lon), dates, scenarios per day, luggage limit, laundry
     available, packing checklist, day outfits.

     InspirationLook — photo, pieces (subcategory, color hex and family, pattern,
     silhouette, optional crop box), style tags, notes.
     CustomRule — enabled, name, condition (all/any of predicates on items and context),
     action (never together, require, forbid, prefer +w, avoid −w, if-then-require), scope
     (scenario, temperature range, weekday).
     WeatherSnapshot — location, fetchedAt, hourly arrays for the next 7 days, daily
     summary for 16 days.
     Settings and profile (DataStore) — style weights, boldness 0–1, personal color module
     (on/off, strength, season, favorites, avoid list), thermal offset °C, rotation windows per
     slot, outfit cooldown days, rejected cooldown days, deck size, exploration rate, rule
     toggles and weights, strictness preset, location mode, indoor temperatures,
     notification times, category hints on/off, weekly scenario template.

Default style weights: minimal 1.0, smart casual 1.0, classic 1.0, streetwear 0.8, sporty 0.8,
all others 0.3. Default boldness 0.4.
```

## SPEC 3 — Photo pipeline

```text
Adding a piece should take under 20 seconds: take the photo, get an automatic cutout
and colors, pick a subcategory, save.

 1. Capture. An in-app CameraX screen with a framing overlay and tips (flat lay or hanger,
     plain contrasting background, daylight). Or the Photo Picker with multi-select for batch
     adds. Or the Android share sheet: share an image from a browser or Instagram into
     Ensemble to create an item or an inspiration look.
 2. Normalize. Fix EXIF orientation, downscale to 2048 px on the long edge, strip all EXIF
     including GPS, save original.jpg .
 3. White balance (classic algorithm). Automatic gray-world correction, plus "tap
     something white or grey" to compute per-channel gains, with a live before/after. Indoor
     light turns navy black and white yellow; this keeps colors honest.
 4. Cutout (ML Kit, toggle). Subject Segmentation → foreground mask → cutout.webp
     with alpha. Touch-up brush (erase / restore, adjustable size) and 1–2 px feathering.
     Fallback when Play services or the model isn't available: border-seeded region growing
     in OKLab with a tolerance slider, keep the largest connected component,
     morphological open/close, then the same brush.
 5. Colors (pure Kotlin in :engine ). Sample cutout pixels with alpha ≥ 0.5 at about 128 px;
     convert to OKLab; weighted k-means++ (k = 5, seeded, 15 iterations); merge clusters
     closer than ΔE_ok 0.05; drop clusters under 4 %; sort by share; map each to the
     nearest color family (Appendix B); propose roles. An eyedropper lets you tap the
     photo to add or replace a color.

 6. Pattern hint. Two or more clusters of 20 %+ that differ by ΔE_ok > 0.15, plus high edge
     density (Sobel on lightness), suggest "patterned?" chips (stripes / check / print) for
     you to confirm.
 7. Category hints (ML Kit Image Labeling, toggle, on by default). Run on the cutout, map
     labels to subcategory candidates with a lookup table Claude Code builds from the
     model's label list, and show the top 3 as chips. Your tap decides; nothing is committed
     automatically. When labels are too coarse, show the visual subcategory grid instead.
 8. Smart defaults. The chosen subcategory pre-fills formality, warmth, seasons, style
    tags, layering flags and laundry threshold. "Duplicate as new color" clones an item, for
     the same tee in five colors.
 9. Storage. filesDir/items/{id}/original.jpg , cutout.webp , thumb.webp (512 px for
     grids). Inspiration looks live under filesDir/looks/{id}/ .

ML Kit notes: declare the com.google.mlkit.vision.DEPENDENCIES meta-data in the
manifest (value subject_segment , plus the labeling model if it is unbundled) so models
download at install. If a model is still downloading on first use, show "Preparing cutouts…"
and fall back gracefully.
```

## SPEC 4 — Color engine

```text
Colors are measured in OKLab for distance and lightness but judged for harmony on a
painter's (RYB) wheel, because the complements stylists use — red/green, blue/orange,
yellow/purple — are RYB complements, not RGB ones.

4.1 Color spaces.

     Convert sRGB → linear → OKLab → OKLCH (Björn Ottosson's matrices). Distance
     ΔE_ok = Euclidean distance in OKLab; L = lightness 0–1; C = chroma.
       artistHue(color) maps HSV hue onto an RYB-style wheel, piecewise-linear: 0–30° →
     0–60°, 30–60° → 60–120°, 60–120° → 120–180°, 120–240° → 180–240°, 240–360°
     unchanged. Unit-test the anchors: red 0°, orange 60°, yellow 120°, green 180°, blue
     240°, purple about 270–300°. With this wheel navy + camel and navy + rust read as the
     near-complementary pairs they are.
     A config switch HueWheel.PERCEPTUAL (plain OKLCH hue) stays available for
     experiments.

4.2 Per-color attributes. L, C, artistHue, family (Appendix B), neutralness 0–1 =
max(family value, 1 − C/0.06) clamped, and temperature: warm for artist hue 330–135°,
cool for 165–300°, neutral in between; neutrals take their family's temperature.

4.3 Outfit color model. Each item has an area weight by slot (Appendix B: a closed coat
0.40, a top 0.30, trousers 0.30, shoes 0.07, a belt 0.02…). A color's area A = item weight ×
color share. Chromatic salience σ = A × (1 − neutralness) × clamp(C / 0.12, 0.3, 1.2). Only
colors with neutralness < 0.8 enter the harmony math.

4.4 Rules. Each returns a score 0–1, a weight and an explanation key.

   ID      Rule                    How it scores

   C1      Harmony template fit    Matsuda hue templates on the artist wheel: i (one
                                   18° sector), V (one 93.6°), L (18° + 79.2°, centers
                                   90° apart), I (two 18°, opposite), T (one 180°), Y
                                   (93.6° + 18°, opposite), X (two 93.6°, opposite). For
                                   every template and rotation in 1° steps, cost = Σ σ ×
                                   arc distance to the nearest sector. Best fit gives
                                   score = 1 − min(1, cost / (Σσ × 45°)), times a
                                   template prior: i, V 1.0 · I 0.95 · L, Y 0.9 · X 0.8 · T 0.75.
                                   Triadic (three hues about 120° ± 20° apart) is an
                                   extra template allowed at boldness ≥ 0.6, prior 0.7.
                                   All-neutral outfits score 1.0 with the label "Neutral".

   C2      Accent count            Count chromatic hue groups (within 25°,
                                   neutralness < 0.6, area ≥ 3 %). Ideal 0–2, up to 3
                                   when boldness > 0.7; each extra group costs 0.3.

   C3      60/30/10 proportion     Dominant color group at 45–70 % of area; a vivid
                                   accent at ≤ 15 %. Two vivid groups each ≥ 30 % is a
                                   "color fight" penalty unless they are
                                   complementary and boldness ≥ 0.6.

   C4      Value contrast          ΔL between the upper-body and lower-body
                                   dominant colors. Target band from personal
                                   contrast: low 0.05–0.20, medium 0.15–0.35, high
                                   0.30–0.60 (medium when the color module is off).
                                   +0.05 when the bottom is darker. Penalty for "grey
                                   mush": everything at L 0.45–0.65 with C < 0.05.

   C5      Chroma balance          At most one vivid color (C ≥ 0.15) above 25 % of
                                   area unless boldness ≥ 0.7; neon (C ≥ 0.22) only as
                                   an accent ≤ 10 %.

   C6      Temperature coherence   Mild bonus when chromatic colors share warm or
                                   cool; mild penalty for vivid warm + vivid cool unless
                                   the fit is template I or Y.

   ID      Rule                      How it scores

   C7      Classic pairing table     Affinity overrides for neutral and classic pairs
                                     (Appendix B: navy + camel 0.95, black + brown
                                     0.35, navy + black 0.5…), averaged over adjacent
                                     slot pairs (top–bottom, outer–bottom, bottom–
                                     shoes, outer–top).

   C8      Denim on denim            Allowed when the washes contrast (ΔL ≥ 0.15) or
                                     match exactly (ΔE ≤ 0.03); otherwise a penalty.

   C9      Near-miss                 Same family with ΔE 0.03–0.08 across top/bottom
                                     or jacket/trousers reads as a failed match (the
                                     "almost navy" suit): penalty. Match it or contrast it.

   C10     Leather harmony           Shoes, belt, bag and watch strap in the same
                                     leather family (ΔL ≤ 0.12). Strict at formality ≥ 6;
                                     relaxed at ≤ 4 or with suede, canvas or sneakers.

   C11     Metal harmony             Watch case, buckle, jewelry and frames share a
                                     metal tone: bonus; mixed: small penalty (toggle
                                     "allow mixed metals").

   C12     Personal color (module)   Only when switched on, scaled by the strength
                                     slider: season palette fit for near-face slots (top,
                                     outer, scarf, cap), favorites bonus, avoid-list
                                     penalty (hard when marked "never"). When off it
                                     contributes nothing and never appears in
                                     explanations.

   C13     Scenario palette          DARK_MUTED (funeral), CONSERVATIVE
                                     (interview: neutrals + light blue, one muted
                                     accent), LIGHT (beach), EVENING (deeper values,
                                     richer accents), ANY.

   C14     Seasonal nudge            Tiny weight: autumn and winter favor deeper
                                     values and earth tones; spring and summer favor
                                     lighter values.

   C15     Wedding white             Dominant white or ecru (≥ 50 % of area) is
                                     penalized in the Wedding guest scenario.

4.5 Personal color module (optional). An on/off switch plus a strength slider (0–100 %) in
Settings.

     Color season. Pick from the 12-season system, or take a short rule-based quiz: vein
     color, gold vs silver, reaction to sun, natural hair and eye color, contrast between them,
     pure white vs cream, black near the face. Answers score three axes — temperature

     (warm / neutral / cool), value (light / medium / deep), chroma (soft / medium / clear) —
     and the nearest season wins. You can always override it; the quiz is a guide, not a
     diagnosis.
     Season data. Each season stores ranges on those three axes plus 10–12 display
     swatches. An item color's fit = how well its temperature, L and C sit inside the ranges,
     plus its distance to the nearest swatch.
     Favorites and avoid list. Favorite colors earn a bonus; avoid colors a penalty, or "never".
     Scope. Applies to near-face slots by default; option "apply to all slots".
```

## SPEC 5 — Style rules: patterns, formality, fit, shoes, accessories, weather, rotation

```text
Hard constraints remove candidates; soft rules score what's left, and their weighted sum
is the Match %.

5.1 Hard constraints (never broken unless you tap "use anyway"):

 1. Availability: status available, not in laundry, not temporarily suppressed.
 2. Structure: (top + bottom) or a one-piece, plus shoes (Home allows none); layers in
     order base → mid → outer; one item per slot except layered tops and accessories.
 3. Layering: the mid layer's bulk must fit the outer layer; no two outer layers (an overshirt
    may be the mid layer under a coat).
 4. Weather: no shorts when the window's feels-like max is below your shorts minimum
     (default 20 °C); no sandals below 22 °C; no heavy knits or coats when the feels-like min
     is above 26 °C; heavy rain (≥ 5 mm in the window) with outdoor share > 0.3 rules out
     suede and canvas shoes.
 5. Scenario: every core piece within the scenario's formality range ±1; required and
     forbidden subcategories and tags (swim shorts only at beach or pool; slides only at
     home, gym, beach or pool).
 6. Outfit cooldown: a worn outfit's signature is excluded until its cooldown ends (5.9).
 7. Never again: outfits marked "never show again" and custom NEVER rules.
 8. Items marked neverSuggest.

5.2 Patterns.

     P1: at most one loud pattern (loudness 3) and two patterned pieces, unless boldness ≥
     0.7.
     P2: two patterns must differ in scale (small + large) or type, and share at least one
     color.
     P3: at most one graphic or big-logo piece; each one lowers formality.

     P4: textures (cable knit, tweed, corduroy) count as low-loudness patterns.

5.3 Materials and texture.

     M1: season fit (linen and seersucker in summer; flannel, tweed, corduroy and heavy
     wool in winter).
     M2: visual weight balance (heavy boots with linen shorts is a penalty; keep the fabric-
     weight spread small).
     M3: sheen consistency in formal outfits.
     M4: tonal or monochrome outfits earn a bonus when textures vary (knit + twill +
     suede).
     M5: technical fabrics with tailoring is a penalty unless the outfit is techwear.

5.4 Formality and style.

     F1 coherence: area-weighted standard deviation of formality stays small; a range above
     4 (hoodie + derbies) is penalized unless your learned "high-low" taste is positive.
     F2 target: mean formality close to the scenario target (Gaussian, σ = 1.2), shifted by the
     learned per-scenario offset.
     F3 classic conflicts (a data table): a tie needs a collared shirt and a blazer; a pocket
     square needs a blazer; a suit jacket wants its own trousers unless "wear as separate" is
     ticked; a hoodie under a blazer or overcoat only with streetwear taste; shorts with a
     blazer only as tailored shorts + unstructured blazer at ≥ 24 °C; athletic shorts outside
     gym, running, home or beach are penalized.
     O1 style coherence: the outfit's style vector (sum of item style tags) should be led by
     one or two styles; pairs are scored with the style matrix (Appendix B), adjusted by
     learned style-mix features.
     O2 the scenario's preferred styles; O3 your style weights; O4 a small bonus for pieces
     you starred as signature.

5.5 Silhouette (simple fit). Top versus bottom volume, scored 0–1 and adjusted by learned
silhouette pairs:

   Top / Bottom     Slim   Regular   Relaxed    Oversized

   Slim             0.9    1.0       0.9        0.7

   Regular          1.0    1.0       0.9        0.6

   Relaxed          0.9    0.9       0.7        0.5

   Oversized        1.0    0.9       0.5        0.3 (0.7 with streetwear)

An outer layer slimmer than the mid layer's bulk is a penalty. Chunky shoes with slim
trousers earn a mild penalty; with relaxed or oversized trousers, a bonus.

5.6 Shoes (a full slot):

     SH1: formality fits the scenario and the rest of the outfit.
     SH2: the shoe × trouser table (Appendix B).
     SH3: weather — rain ≥ 50 % with outdoor exposure penalizes suede, canvas and knit
     and boosts rubber soles and weatherproof boots; snow or ice calls for lug-sole boots; ≥
     28 °C boosts breathable shoes and penalizes boots.
     SH4: leather harmony with belt and bag (C10).
     SH5: rest days — leather shoes worn yesterday are penalized (default restDays 1) so
     they can dry out.
     SH6: at formality ≥ 6, shoes as dark as or darker than the trousers (toggle; white
     sneakers exempt).
     SH7: a sock hint in the explanation only ("no-show socks", "match socks to trousers").

5.7 Accessories (full set, added in a second pass after the core outfit):

     AC1 budget: count within the scenario's accessory range.
     AC2 belt: required at formality ≥ 6 when the trousers have belt loops; never with
     joggers, sweatpants or loop-less shorts; leather matches the shoes.
     AC3 watch: a dress watch at ≥ 7; sport or digital for active scenarios; metals match.
     AC4 bag: backpack for university and travel; leather briefcase or messenger at ≥ 6;
     leather harmony.
     AC5 headwear: none at ≥ 7; beanie when feels-like ≤ 10 °C; cap when UV ≥ 6 or the
     outfit is streetwear or sporty; never two.
     AC6 sunglasses: UV ≥ 5, outdoors, daylight in the window.
     AC7 scarf: feels-like ≤ 8 °C or wind ≥ 30 km/h; its color joins the palette as a near-face
     color.
     AC8 jewelry: at most two subtle pieces for minimal and classic; chains welcome with
     streetwear; metals match.

5.8 Weather and comfort.

     W1 warmth: ensemble insulation I_cl = 0.835 × Σ item clo + 0.161 (ISO 9920 summation).
     Outdoors (outerwear on): target from the feels-like table in Appendix C, adjusted by
     activity (sedentary +0.2, active −0.2) and your learned thermal offset. Indoors
     (outerwear off): target 0.5 clo at 25 °C rising linearly to 1.0 clo at 20 °C, ±0.15; indoor
     temperature defaults to 22 °C from October to April and 25 °C from May to September
     (settings). W1 = indoor share × indoor score + (1 − indoor share) × outdoor score.

     W2 temperature swing: a range ≥ 8 °C in the window boosts removable layers (open-
     front, packable), and the outfit without them must still work (presentableAlone).
     W3 rain: water-resistant outerwear, an umbrella reminder, light trousers mildly
     penalized.
     W4 wind ≥ 30 km/h: windproof outerwear boosted.
     W5 sun: UV ≥ 6 outdoors boosts cap, sunglasses, light colors and breathable fabrics.
     W6 heat ≥ 28 °C: linen and cotton boosted; dark heavy fabrics and non-breathable
     synthetics penalized.
     W7 snow or ice: boots with grip.

5.9 Rotation and cooldowns.

     R1 item rotation: a soft penalty that fades over each slot's window (defaults: base tops
     7 days, mid layers 4, jeans 2, other trousers 3, outerwear 0, accessories 0). "Strict
     rotation" makes it hard.
     R2 outfit cooldown: after you wear an outfit, its signature is hidden for N days (default
     14; global setting; per-outfit override). Near-duplicates sharing 3 of 4 core pieces get a
     soft penalty in the same window. Saved outfits show "Back in 5 days" with a "Use
     anyway" button.
     R3 rejected cooldown: a swiped-No outfit stays hidden 21 days (setting); "never show
     again" makes it permanent.
     R4 deck freshness: outfits shown in the last 3 decks are penalized.
     R5 forgotten pieces: items unworn for 30+ days get a small boost (toggle "give
     forgotten pieces some love").
     R6 plan conflicts: pieces planned for the next 3 days are penalized today.
     R7 laundry: items at their wash threshold are mildly penalized.

5.10 Custom rules. A builder in Settings: IF item attributes (subcategory, color family, tag,
pattern, material, style, a specific item) and context (scenario, temperature range,
weekday) THEN never together / require / forbid / prefer +w / avoid −w. Examples: "never
brown shoes with black trousers", "Office → require a collared top", "prefer white
sneakers above 18 °C".

5.11 Scoring.

     Group weights (defaults): color 0.24 · weather 0.16 · formality and style 0.14 · shoes and
     accessories 0.08 · patterns and texture 0.08 · silhouette 0.06 · rotation 0.06 · learned
     taste 0.18.
     Learned taste ramps from 0.02 with no feedback to 0.18 at 60 weighted events; the
     "Trust my taste" slider moves its ceiling between 0.10 and 0.30. Weights always
     renormalize to 1.

     A group's score is the weighted mean of its rules; Match % = round(total × 100).
     Strictness presets: Classic (boldness 0.2, strict patterns and formality), Balanced
     (default, 0.4), Adventurous (0.75, triadic and color blocking allowed).
     Every rule result carries an explanation key and arguments; the "why" panel shows the
     top 3 positives by weight × (score − 0.5) and the top 1–2 trade-offs.
```

## SPEC 6 — Outfit generator

```text
The generator builds a deck by beam search over your wardrobe, scores complete outfits
with every rule, then picks a varied set; the same inputs always give the same deck.

 1. Pools. Apply hard constraints per slot: availability, weather, scenario formality, season.
 2. Structures. Pick candidate slot structures from the feels-like band (Appendix C): for
     example ≥ 28 °C top + bottom + shoes; 16–20 °C top + mid or light outer; below 8 °C top
     + mid + warm outer with scarf and beanie candidates. The clo model has the final say
     on warmth.
 3. Pair cache. Precompute a compatibility score for every item pair in different slots
     (color, formality, pattern, style, silhouette, material, shoe table). Update it
     incrementally when an item is added or edited, and persist it so cold start stays fast.
 4. Beam search. Locked pieces first, then bottom → top → shoes → mid → outer. Beam
     width 120; each step tries the top 40 candidates by pair score with the partial outfit;
     hard constraints are checked as early as possible.
 5. Full scoring. Score every complete candidate (a few thousand at most) with all soft
     rules; keep the top 200.
 6. Accessories pass. Greedy best-improvement within the scenario's budget (belt,
     watch, bag, headwear, sunglasses, scarf, jewelry), honoring C10, C11 and the AC rules. A
     toggle hides accessories.
 7. Diversify. Maximal marginal relevance with λ = 0.75; similarity = shared item area + 0.2
     for the same harmony label. Aim for at least 3 harmony labels and 4 different tops per
     12-card deck when the wardrobe allows.
 8. Explore. About 1 card in 5 (setting 0–30 %) comes from rule-valid candidates above the
     70th percentile whose features have the least feedback (bonus Σ 1/√(1 + n)). These
     cards are labeled "New idea".
 9. Explain. Attach the why and trade-off lines plus a weather line ("Feels 12 → 19 °C: the
     overshirt comes off at lunch").
10. Determinism. Seed = hash(date, scenario, regenerate count, wardrobe version).

Modes on the same engine: Today's deck · Build around a piece (lock items) · Complete
my outfit (composer) · Rate my outfit (score anything you assemble) · Swap one piece
(ranked alternatives for one slot) · Recreate an inspiration look.

Performance budget. 300 items → a 12-card deck in under 300 ms on a mid-range phone.
A JVM benchmark test with 300 synthetic items logs the time and fails above 1.5 s.
```

## SPEC 7 — Learning without AI

```text
Taste is learned by counting: each feedback event adds positive or negative weight to
named features of the outfit, and those counts, decayed over time, nudge future scores
— no models, fully explainable and replayable.

Feature keys extracted from an outfit:

       item:{id} ; pair:{a}|{b} for core adjacent pairs (top–bottom, bottom–shoes, outer–
     top, outer–bottom, mid–top)
       fam:{a}|{b} dominant color families of adjacent pairs; harmony:{label} ; accent:
     {family} ; contrast:{low|med|high}
       style:{tag} (top 2); stylemix:{a}|{b} ; sil:{top}|{bottom} ; shoe:{shoeSub}|
     {bottomSub}

       pattern:{type} ; patternmix:{a}|{b} ; formality:{scenario}:{bucket} ; layers:
     {n}|{tempBand} ; material:{m}|{season} ; acc:{sub}|{scenario}

Context keys. Every update writes to GLOBAL (×1.0), SCN:{scenario} (×1.0) and SEASON:
{s} (×0.5). Reading blends 0.5 scenario + 0.3 global + 0.2 season, renormalized when one is
empty.

Signals.

   Event                        Weight                Goes to

   Swipe right (yes)            +1.0                  All features

   Swipe left (no), no reason   −1.0, split           Combination features 0.6 each, single
                                                      items 0.15, the rest 0.3 — a rejection
                                                      usually means "this combination", not
                                                      "this item"

   Swipe left with a reason     −1.0                  Only the features the reason names
   chip                                               (table below)

   Swipe up (wearing it)        +2.5                  All features; also logs the wear, starts
                                                      the cooldown, counts toward laundry

   Save                         +2.0                  All features

   Composed by you              +3.0                  All features

   Event                           Weight                   Goes to

   Piece thumbs up / down          ±1.5                     That item in this scenario; ±0.3 to its
   (long-press)                                             pairs in this outfit

   After-wear check-in             felt 5 = +2 down to      Outfit features; comfort ≤ 2 adds a
                                   felt 1 = −2              small per-item comfort penalty; the
                                                            thermal answer moves the thermal
                                                            offset ±0.5 °C (bounded ±5)

   Inspiration look added          +2.0                     Abstract features only: families,
                                                            harmony, silhouette, style, pattern,
                                                            shoe type

   Never show again                block + −2.0             Signature blocked; pair features

"Why not?" chips (appear for 4 seconds after a No, skippable):

   Chip                            Effect

   Colors don't work               Color features only (families, harmony, accent,
                                   contrast)

   Too formal / Too casual         Scenario formality offset ∓0.25 (bounded ±2) +
                                   formality features

   Too warm / Too cold             Thermal offset ±0.5 °C

   Fit / proportions               Silhouette and shoe features

   Not this piece (then pick it)   That item −1.0 in this scenario + hidden for 3 days

   Too boring / Too loud           Boldness ±0.03; "too loud" also penalizes pattern
                                   mixes

   Wore it recently                Rotation window for that slot +1 day (bounded)

   Not today                       No learning; the outfit is hidden for 1 day

Math.

     Decayed counts: each weight × 0.5^(age in days / 120). The half-life is a setting.
     For one feature in one context: mean = (pos + 1) / (pos + neg + 2); lift = 2 × (mean − 0.5);
     confidence = n / (n + 3) where n = pos + neg; value = lift × confidence.
     Learned score = 0.5 + 0.5 × tanh(1.5 × Σ over groups of weight × mean value), with
     group weights items 0.20, pairs 0.25, colors 0.20, style 0.15, silhouette and shoes 0.10,
     other 0.10.

Taste profile screen. Top and bottom 10 color pairs, items, styles and silhouettes; thermal
offset; formality offset per scenario; "Forget this" per feature; "Reset all learning" (soft:
start a new reset epoch; hard: delete the log); "Rebuild from history" (deterministic
replay).

Tests. Identical event lists give identical stats; decay halves a weight at the half-life; a
reason touches only its features; scenarios stay isolated (Night out likes don't move
Office formality); the ramp follows its formula; rebuild equals incremental.
```

## SPEC 8 — Weather, scenarios, planner, laundry, trips, gap finder, inspiration

```text
These modules either feed the engine its context or reuse it at a bigger scale; all of them
are deterministic.

8.1 Weather.

     Open-Meteo forecast API, no key: hourly temperature_2m , apparent_temperature ,
       precipitation_probability , precipitation , wind_speed_10m , uv_index ,
       relative_humidity_2m , weather_code ; daily min/max plus sunrise and sunset;
       timezone=auto ; forecast_days=16 . Claude Code verifies parameter names against
     the current docs.
     Open-Meteo Geocoding API for city search (saved cities, trip destinations).
     Location: coarse permission only (city-level is enough), with a saved-city fallback.
     Cache in Room; refresh on app open when older than 3 h and each morning through
     WorkManager. Offline: show the last cache with its age, plus a manual temperature
     override per day.
     Scenario window aggregates: feels-like min and max over the window's hours, max rain
     probability, rain sum, max wind, max UV, and "dark" when sunset falls inside the
     window.
     Show "Weather data by Open-Meteo.com" with a link wherever forecasts appear: the
     data is licensed CC BY 4.0, which requires credit.
     Licence: the free API is for non-commercial use, under 10,000 calls a day. A paid app or
     ads would need an Open-Meteo commercial plan.

8.2 Scenarios. Presets in Appendix C, all editable, plus custom scenarios with the same
fields. A weekly template sets the default scenario per weekday (asked in onboarding:
university, office, mixed or varies).

8.3 Weekly planner.

     Inputs: the next 7 days of forecast, a scenario per day, locked days.

     Greedy in date order: each day takes the best deck result under rotation, outfit
     cooldown, shoe rest days and laundry projection (wear counts rise during the week).
     Then one improvement pass swaps days whenever the total score rises.
     Shows a plan score and conflicts; drag to swap days; replans automatically when a
     day's forecast moves by ≥ 4 °C or rain flips.

8.4 Laundry and wear log.

     Swipe up or "Wear today" writes a WearLog entry, increments wearsSinceWash, starts
     the outfit cooldown and schedules the evening check-in.
     At an item's wash threshold: ask "Move to laundry?" or move it automatically (setting).
     A laundry basket screen with "Mark all washed".
     A history calendar of worn outfits with that day's weather; cost per wear on every item
     with a price.

8.5 Trip packing.

     Inputs: destination (geocoding), dates, scenarios per day, laundry available or not,
     luggage limit (item count, or carry-on ≈ 12–14 pieces), shoe limit (default 2–3).
     Forecast for up to 16 days ahead; beyond that you enter the expected temperature
     range.
     Greedy maximum coverage: start empty; repeatedly add the piece with the best gain =
     new distinct good outfits (Match ≥ 70) covering still-uncovered days, divided by its
     cost (1; shoes and outerwear 1.5). Stop when every day has a distinct outfit or a limit is
     reached.
     Assign outfits to days without repeats where possible. Output a packing checklist, a
     day-by-day plan and "wear on the travel day" (the bulkiest pieces).

8.6 Gap finder and insights.

     Archetype catalog (Appendix C): simulate adding each archetype in each typical color,
     count the new outfits with Match ≥ 75 across the scenarios and temperature bands
     you actually use (weighted by use), and rank: "Grey merino crewneck → unlocks 14
     outfits, fills Office below 10 °C".
     Orphans: pieces in fewer than 3 good outfits, with what would pair with them.
     Insights: wardrobe palette by color family, wears per item, cost per wear, unworn for 60
     or 90 days, a scenario × temperature coverage heatmap, total possible outfits.

8.7 Inspiration looks.

     Import a photo (share sheet or picker) and mark its pieces: subcategory, eyedropper
     color from the photo, pattern, silhouette.

     Recreate: per piece, similarity = 0.45 subcategory (same 1.0, same group 0.6, same slot
     0.3) + 0.35 color (1 − ΔE/0.25, clamped, plus a family-match bonus) + 0.10 pattern + 0.10
     silhouette. A beam over the top 3 matches per piece maximizes 0.6 × similarity + 0.4 ×
     outfit score.
     Output: match % per piece and overall; pieces under 0.5 are "missing" and link to the
     gap finder.
```

## SPEC 9 — Screens and UX

```text
Five tabs — Today, Wardrobe, Outfits, Plan, You — built around a swipe deck, in a grey
interface that never tints how your clothes look.

Today.

     A weather card for the selected scenario's window (icon, feels-like range, rain %,
     wind), scenario chips, and a day picker (today, tomorrow, pick a date).
     The deck. Each card: the collage on a neutral studio-grey backdrop, Match %, a
     harmony chip ("Analogous · earth"), a formality chip, a weather chip, a one-line why, and
     a "New idea" label on exploration cards.
     Gestures: right = like; left = no (then the "why not?" chip row slides up for 4 s); up =
     wearing it; tap = details; long-press a piece = like or dislike that piece, swap, lock, open
     the item. Commit at 30 % of the width or a fling above 1000 dp/s; the card rotates up to
     ±12°; a haptic tick on commit; LIKE / NOPE / WEAR stamps in Ensemble's own style.
     Undo the last 5. On-screen buttons ✕ ♥ ↑ and TalkBack custom actions do the same
     things.
     End of the deck: "More", "Bolder" and "Safer".
     Empty state: "Add at least 2 tops, 2 bottoms and 1 pair of shoes" with a progress ring.

Outfit detail. A large collage; one row per piece with swap; "Why this works" (top 3) and
"Trade-offs"; the weather line; actions Wear today · Save · Plan for… · Never show again.
Debug builds add a table of every rule's score.

Wardrobe.

     A grid of cutouts; filter chips (slot, color family, season, status, style, custom tag); sort
     by recent, most or least worn, formality, or color in hue order (a rainbow closet); search;
     multi-select with bulk actions (laundry, status, season).
     The item editor from SPEC 3. Item detail: photos, attributes, wears, last worn, cost per
     wear, "Pairs best with" (top 6 from the pair cache), "Outfits with this" (build around),
     status toggles, duplicate as new color.

Outfits. Segments: Saved · Composed · Inspiration · History (calendar). The composer is a
slot board: drag pieces in from a filtered sheet, see a live Match % and warnings with one-
tap fixes ("Brown belt clashes with black shoes → swap to the black belt?"), and tap
"Complete it". Cooldown badges read "Back in 5 days".

Plan. A week strip with forecast and scenario per day, assigned outfit thumbnails, Auto-
plan, drag to swap, lock days. Cards for Trips and Laundry.

You. Style profile and boldness, taste profile, rules and weights, strictness preset, custom
rules, scenarios, personal color module, insights and gap finder, rotation and cooldown
settings, location and weather, notifications, data (export, import, delete all), about.

Onboarding. Welcome → style seed (minimal, smart casual/classic, streetwear/sporty
pre-selected) → boldness slider with three example palettes → "I run cold / neutral / hot" →
usual weekday (university, office, mixed, varies) → location → personal color module (skip,
pick a season, or take the quiz) → notifications → "Add your first pieces" with photo tips
and the minimum-set checklist.

Notifications and widget.

     Morning (default 07:30): "16 → 23 °C, dry. Today: navy overshirt, white tee, grey chinos",
     with actions Wear it · See more.
     Evening check-in (default 21:00, only on days you wore something): comfort,
     temperature, how it felt, a note.
     Glance widget: today's top pick and forecast; tap to open.

Visual design.

     An achromatic interface with no dynamic color: light theme near-neutral #F5F5F4 with
     neutral grey surfaces; dark theme #121212. One ink color for primary actions (black in
     light mode, white in dark); green, red and blue appear only on the swipe stamps.
     Collage backdrop #E9E9E7 (light) and #2A2A2A (dark).
     Typography: a display serif for headings (for example Fraunces, OFL licence) and Inter
     for the interface.
     Spring physics on swipes; 48 dp touch targets; WCAG AA contrast; every color also
     shown by name, which helps color-blind users.
```

## SPEC 10 — Storage, privacy, testing, performance, release

```text
Everything lives on the phone, is backed up by an export you control, and is tested
hardest where mistakes would hurt most: the engine.

Storage.

     Room tables: items, item_colors, item_photos, outfits, outfit_items, scenarios,
     feedback_events, preference_stats, wear_logs, check_ins, day_plans, trips, trip_items,
     inspiration_looks, look_pieces, custom_rules, weather_cache, pair_cache.
     Exported schemas in data/schemas ; a migration and a migration test for every change.
     Export: a .zip with manifest.json (schema version, app version, date), data.json
     and the images, saved through the system file picker. Import: merge or replace, with a
     preview of the counts first.
     "Delete all data" with a typed confirmation.

Privacy. No analytics, no accounts, no ads. Coarse location only. EXIF is stripped from
every photo. The only data that leaves the phone is your approximate coordinates (or a
city name) sent to Open-Meteo for the forecast; the privacy policy and Play's Data safety
form must say exactly that.

Testing.

   Layer       What is tested

    :engine    Every rule fires and doesn't fire; color conversions
               against reference values; artist-hue anchors; k-
               means determinism; harmony templates; generator
               invariants as property tests (never breaks a hard
               constraint, locked pieces always present,
               deterministic per seed); learning; planner, packing
               and gap finder; the golden cases; the 300-item
               benchmark

    :data      DAOs, migrations, repositories with fakes, weather
               parsing from recorded JSON fixtures

    :app       ViewModels with Turbine; Compose UI tests for
               the swipe deck and the item editor; optional
               screenshot tests (Roborazzi)

   Manual      A short checklist per phase, run on your phone

A fixture wardrobe, demo_wardrobe.json (about 45 items with placeholder art: a
category silhouette tinted with the item's color), powers the tests and the debug "Seed
demo wardrobe" action.

Performance budgets. Cold start < 1.5 s · deck < 300 ms at 300 items · cutout < 2 s · add-
item flow < 20 s · smooth swipes at the display's refresh rate · thumbnails only in lists · a
baseline profile and R8 for release builds.

Play Store readiness (for later):

     Final application ID before the first upload, and an app name that isn't taken.
     A hosted privacy policy, the Data safety form, a content rating, target SDK at Play's
     current minimum, Play App Signing, an Android App Bundle.
     Store assets: 512 px icon, 1024 × 500 feature graphic, phone screenshots.
     Personal developer accounts created after 13 November 2023 must run a closed test
     with at least 12 testers opted in for 14 continuous days before applying for production;
     organization accounts are exempt.
     If the app ever earns money, move to an Open-Meteo commercial plan.

Out of scope for v1. Social features, shopping links, cloud sync or accounts, any AI,
womenswear (the taxonomy is data, so it can come later), virtual try-on, multiple profiles.
```

## Phase prompts

```text
Twelve phases, each in a fresh Claude Code session in plan mode: paste the standard
opener, then that phase's block.

Standard opener (paste before every phase):

   Read CLAUDE.md, PROGRESS.md and DECISIONS.md, then the docs/SPEC.md sections
   named
   below. Stay in plan mode: propose a step-by-step plan with the files you will
   touch
   and the tests you will write, and wait for my approval. When you finish: run
   ./gradlew :engine:test test assembleDebug lint, install on my phone if it is
   connected, update PROGRESS.md, and make one commit. Then tell me exactly what
   to
   check by hand on the phone.

Phase 0 — Foundation (SPEC 1, CLAUDE.md)

   Phase 0. Restructure the Android Studio template into :app, :core:model,
   :engine and
   :data with one shared version catalog. Add Hilt, Room, DataStore,
   kotlinx.serialization, Coil and the test stack. Set up the achromatic
   Material 3
   theme (light + dark, fonts from SPEC 9) and a bottom bar with five
   placeholder tabs.
   Give :engine one sample unit test. Done when: the app builds and installs,
   the tabs
   navigate, and :engine:test runs on the JVM in seconds.

Phase 1 — Model, taxonomy, color science (SPEC 2, SPEC 4.1–4.2, Appendices A and B)

   Phase 1. In :core:model and :engine: all domain types from SPEC 2; the
   menswear
   taxonomy with its defaults from Appendix A, as data; color families, the
   pairing
   table, slot area weights, the shoe x trouser table and the style matrix from
   Appendix B, as data; sRGB<->OKLab/OKLCH, deltaE_ok, artistHue, neutralness
   and
   temperature. Done when: tests cover conversions against reference values, the
   artist-hue anchors, nearest-family mapping for every family's own hex, and
   complete
   defaults for every subcategory.

Phase 2 — Data layer and demo wardrobe (SPEC 2, SPEC 10)

   Phase 2. In :data: Room entities, DAOs and repositories for every record in
   SPEC 2,
   DataStore settings with the defaults listed there, exported schemas, and the
   image
   storage layout from SPEC 3. Create demo_wardrobe.json (about 45 menswear
   items
   covering every slot, with placeholder art: a category silhouette tinted with
   the
   item color) and a debug-only "Seed demo wardrobe" action. Done when: DAO and
   repository tests pass and the seeded wardrobe shows in a temporary list
   screen.

Phase 3 — Wardrobe and photo pipeline (SPEC 3, SPEC 9 Wardrobe)

   Phase 3. Build the Wardrobe tab (grid, filters, sort by hue, search, multi-
   select)
   and the full add/edit flow: CameraX capture, Photo Picker multi-select,
   share-intent import, EXIF fix and strip, white balance (auto + tap), ML Kit
   cutout
   with the fallback and the touch-up brush, k-means color extraction in
   :engine,
   eyedropper, pattern hint, ML Kit category hints behind a toggle, the
   subcategory
   grid with smart defaults, duplicate as new color, item detail. Done when: I
   can add
   a real shirt in under 20 seconds and its colors look right.

Phase 4 — Rules engine and scoring (SPEC 4.3–4.5, SPEC 5)

   Phase 4. In :engine only: the outfit color model, every hard constraint and
   soft
   rule in SPEC 4 and SPEC 5 (C1-C15, P, M, F, O, S, SH, AC, W, R, custom
   rules), the
   clo model, group weights, strictness presets, Match % and explanation keys.
   Add a
   debug "Rate my outfit" screen in :app: pick pieces, see the score and every
   rule's
   contribution. Done when: every rule has firing and non-firing tests, and the
   golden
   cases that only need scoring pass.

Phase 5 — Weather, location and scenarios (SPEC 8.1–8.2, Appendix C)

   Phase 5. Open-Meteo forecast and geocoding client with recorded JSON fixtures
   in
   tests, Room cache, WorkManager morning refresh, coarse location with a saved-
   city
   fallback, manual override, scenario window aggregates, Open-Meteo
   attribution.
   Scenario presets from Appendix C plus a scenario editor. Done when: the Today
   tab
   shows the right window forecast for my location and for a custom scenario.

Phase 6 — Generator and Today deck (SPEC 6, SPEC 9 Today)

   Phase 6. Implement the generator in :engine (pools, structures, pair cache,
   beam
   search, full scoring, accessories pass, MMR, exploration, explanations,
   seeding)
   plus the 300-item benchmark. Build the Today deck UI: swipe gestures, stamps,
   buttons, undo, outfit detail, swap a piece, lock and regenerate, build around
   a
   piece. Record feedback events but don't learn from them yet. Done when: the
   deck is
   varied, deterministic per seed, and inside the time budget.

Phase 7 — Feedback and learning (SPEC 7)

   Phase 7. Feature extraction, the event log, decayed stats, reason routing,
   context
   blending, the ramped learned score, thermal and formality offsets. UI: "why
   not?"
   chips, swipe up = wearing it (WearLog + cooldown + laundry count), piece
   thumbs via
   long-press, the evening check-in notification and sheet, and the taste
   profile
   screen with forget, reset and rebuild. Done when: the SPEC 7 tests pass and
   ten
   "colors don't work" swipes on navy + black visibly demote that pairing.

Phase 8 — Outfits, composer, inspiration (SPEC 8.7, SPEC 9 Outfits)

   Phase 8. Outfits tab: Saved, Composed, Inspiration, History calendar.
   Composer with
   live Match %, warnings with one-tap fixes, and "Complete it". Inspiration
   import,
   piece marking with the eyedropper, recreate with per-piece match and missing
   pieces. Cooldown badges with "use anyway". Done when: I can save a look from
   a
   screenshot and get my closest version of it.

Phase 9 — Planner, laundry, trips (SPEC 8.3–8.5, SPEC 5.9)

   Phase 9. Weekly planner with auto-plan, locks, drag-to-swap, conflict display
   and
   forecast-change replans. Laundry basket, wash thresholds, the auto-move
   setting,
   cost per wear. Trip packing with greedy coverage, a checklist and a day plan.
   Rotation and cooldown settings UI. Done when: a planned week never repeats a
   top
   inside its window and a 5-day trip packs within its limit.

Phase 10 — Insights, gap finder, custom rules, personal color (SPEC 8.6, SPEC 5.10, SPEC
4.5, Appendix C)

   Phase 10. Insights screen and the gap finder with the archetype catalog from
   Appendix C; orphan pieces. Custom IF/THEN rule builder stored as JSON.
   Personal
   color module: on/off switch, strength slider, season picker, rule-based quiz,
   favorites and avoid list, near-face scoring. Done when: switching the color
   module
   off removes every trace of it from scores and explanations.

Phase 11 — Polish and release readiness (SPEC 9 onboarding, SPEC 10)

   Phase 11. Onboarding flow, morning notification with actions, Glance widget,
   export/import zip with a preview, delete-all, an accessibility pass (TalkBack
   actions, color names, contrast, touch targets), a dark-mode check, a
   performance
   pass with a baseline profile, an R8 release build, the app icon. Then add the
   remaining Play Store steps from SPEC 10 to PROGRESS.md as a checklist. Done
   when: a
   release build installs cleanly and passes every golden case.
```

## Golden test cases

```text
These 18 cases on demo_wardrobe.json prove the engine behaves like a good stylist; they
live in :engine tests and must pass from the phase that introduces them onward.

   #       Setup                                      Expected

   1       Office, 15 °C, dry: navy blazer, white     Match ≥ 85; the why lists neutral harmony
           oxford, mid-grey wool trousers, brown      and matching leather
           loafers, brown belt

   2       Same outfit with black derbies and the     Leather rule fires; ranks below case 1
           brown belt

   3       Feels-like max 8 °C, any scenario          No shorts on any card

   4       30 °C and humid                            No wool knit or coat; the linen shirt is
                                                      boosted

   5       Bold check overshirt + floral shirt        Pattern penalty; ranks below solid
                                                      alternatives

   6       Oversized hoodie + oversized cargo,        Silhouette penalty; with a streetwear
           classic profile                            profile, neutral or better

   7       80 % rain, outdoor share 0.6               Suede chelsea boots excluded; a water-
                                                      resistant jacket chosen

   8       An item in the laundry                     Never appears

   9       Same seed and same data                    Identical deck, card for card

   10      Ten "colors don't work" swipes on navy +   The navy/black color-pair feature drops
           black outfits                              and those outfits fall in rank; the items
                                                      themselves barely move

   11      Likes recorded in Night out                Office formality offset unchanged

   #        Setup                                         Expected

   12       Feedback 240 days old, half-life 120 days     Counts at one quarter of its weight

   13       300 synthetic items                           12-card deck under 1.5 s on the JVM
                                                          (target 300 ms on the phone)

   14       Navy + rust; cobalt + orange; red + green     Neutral + accent; complementary;
                                                          complementary on the RYB wheel

   15       Wedding guest, cream suit as the              Wedding-white penalty
            dominant color

   16       Build around a locked piece                   The locked piece is on every card

   17       Outfit worn yesterday, cooldown 14 days       Absent from decks for 14 days; its badge
                                                          says when it returns

   18       Leather loafers worn yesterday                Shoe-rest penalty today; no penalty the
                                                          day after
```

## Appendix A — Menswear taxonomy and defaults

```text
Picking a subcategory fills in every column below; each value is a starting point you can
edit per item, and the clo values are approximations your check-ins tune through the
thermal offset.

Tops and mid layers

  Subcategory        Slot (layer)   Formality Clo    Style tags             Wash    Notes
                                                                            after

  T-shirt            Top (base)     2         0.08   minimal, streetwear,   1       presentable alone
                                                     sporty

  Graphic tee        Top (base)     1         0.08   streetwear             1       pattern = graphic

  Long-sleeve tee    Top (base)     2         0.12   minimal, streetwear    1

  Tank top           Top (base)     1         0.06   sporty                 1       formality ≤ 2
                                                                                    scenarios only

  Henley             Top (base)     3         0.15   minimal, rugged        1

  Polo (piqué)       Top (base)     4         0.14   classic, sporty,       1
                                                     preppy

  Knit polo          Top (base)     5         0.17   classic, minimal       2

  Oxford shirt       Top (base)     5         0.25   classic, smart         2       can be worn open
                                                     casual, preppy                 over a tee

  Subcategory      Slot (layer)   Formality Clo        Style tags             Wash        Notes
                                                                              after

  Dress shirt      Top (base)     7          0.25      classic                1           tucked at formality
                                                                                          ≥6

  Linen shirt      Top (base)     4          0.18      classic, resort        1           summer

  Camp-collar      Top (base)     3          0.15      streetwear, resort     1
  shirt

  Flannel shirt    Top or mid     3          0.30      rugged                 2           can be worn open

  Overshirt        Mid or light   4          0.30      minimal, rugged,       4           can be worn open
                   outer                               smart casual

  Crewneck         Mid or top     2          0.34      streetwear, sporty,    3
  sweatshirt                                           minimal

  Hoodie           Mid or top     1          0.34      streetwear, sporty     3           bulk 2

  Quarter-zip      Mid            4          0.30      sporty, preppy,        3
                                                       smart casual

  Merino           Mid or top     5          0.22      minimal, classic       4
  crewneck

  Chunky knit      Mid or top     4          0.36      rugged, classic        5           bulk 3

  Cardigan         Mid            5          0.25      classic, minimal       4           can be worn open

  Turtleneck       Top or mid     6          0.30      minimal, classic       3

  Sweater vest     Mid            4          0.12      preppy, classic        4

  Gilet / puffer   Mid or         2          0.25      sporty, outdoor        10
  vest             outer

  Fleece           Mid or         2          0.35      outdoor, sporty        6
                   outer

Outerwear

  Subcategory      Slot (layer)       Formality Clo      Style tags               Wash    Notes
                                                                                  after

  Denim jacket     Light outer        3         0.30     streetwear, rugged       15

  Bomber           Light outer        3         0.35     streetwear,              15
                                                         minimal

  Harrington       Light outer        4         0.30     classic, minimal         15

  Chore jacket     Light outer        3         0.35     rugged, minimal          15

  Coach / track    Light outer        2         0.20     sporty, streetwear       10
  jacket

  Varsity jacket   Light outer        2         0.40     streetwear, preppy       15

  Subcategory      Slot (layer)       Formality Clo       Style tags              Wash       Notes
                                                                                  after

  Rain shell       Light outer        2            0.15   outdoor, sporty         15         waterproof

  Unstructured     Light outer        6            0.32   smart casual,           20
  blazer           (tailored)                             classic

  Sport coat       Light outer        7            0.36   classic                 20
                   (tailored)

  Suit jacket      Light outer        9            0.40   classic                 20         wants its matching
                   (tailored)                                                                trousers

  Field jacket     Outer              4            0.40   rugged, classic         15

  Leather jacket   Outer              4            0.40   streetwear, rugged      30

  Suede jacket     Outer              5            0.40   classic                 30         rain-sensitive

  Trench coat      Outer              7            0.45   classic, minimal        20         water-resistant

  Peacoat          Warm outer         6            0.55   classic                 30

  Wool overcoat    Warm outer         8            0.60   classic, minimal        30

  Parka            Warm outer         3            0.70   outdoor,                30
                                                          streetwear

  Puffer jacket    Warm outer         2            0.75   streetwear,             30
                                                          outdoor, sporty

Bottoms

  Subcategory      Slot           Formality Clo      Style tags               Wash        Notes
                   (layer)                                                    after

  Jeans            Bottom         3         0.24     minimal, streetwear,     6           belt loops; dark or raw
                                                     rugged                               +1 formality

  Chinos           Bottom         5         0.20     smart casual, classic,   3           belt loops
                                                     minimal

  Wool trousers    Bottom         7         0.26     classic                  3

  Suit trousers    Bottom         9         0.26     classic                  3           match the suit jacket

  Pleated wide     Bottom         6         0.22     classic, minimal         3
  trousers

  Linen trousers   Bottom         4         0.15     resort, classic          2           summer

  Corduroy         Bottom         4         0.26     rugged, classic          3           winter
  trousers

  Cargo pants      Bottom         2         0.22     streetwear, rugged,      4
                                                     outdoor

  Joggers          Bottom         2         0.24     sporty, streetwear       3           no belt loops

  Subcategory       Slot      Formality Clo    Style tags             Wash    Notes
                    (layer)                                           after

  Sweatpants        Bottom    1         0.26   sporty, streetwear     3       no belt loops

  Track pants       Bottom    1         0.18   sporty                 3       no belt loops

  Chino shorts      Bottom    2         0.06   smart casual, preppy   2

  Tailored shorts   Bottom    4         0.06   smart casual, resort   2

  Denim shorts      Bottom    2         0.06   streetwear             3

  Athletic shorts   Bottom    1         0.05   sporty                 1

  Swim shorts       Bottom    1         0.04   resort                 1       beach or pool only

Shoes

  Subcategory            Slot      Formality Clo       Style tags                 Wash    Notes
                         (layer)                                                  after

  Minimal leather        Shoes     4         0.03      minimal, smart casual,     —       standard profile
  sneakers                                             streetwear

  Canvas sneakers        Shoes     3         0.02      streetwear, minimal        —       rain-sensitive

  Chunky sneakers        Shoes     2         0.04      streetwear, sporty         —       chunky profile

  High-top sneakers      Shoes     2         0.04      streetwear                 —

  Running shoes          Shoes     2         0.03      sporty                     —

  Loafers                Shoes     6         0.03      classic, smart casual      —       sleek; sockless
                                                                                          OK

  Derbies                Shoes     7         0.04      classic                    —

  Oxfords                Shoes     9         0.04      classic                    —       sleek

  Brogues                Shoes     6         0.04      classic                    —

  Monk straps            Shoes     7         0.04      classic                    —

  Chelsea boots          Shoes     6         0.08      minimal, classic, rugged   —

  Chukka / desert        Shoes     5         0.07      smart casual, rugged       —       often suede
  boots

  Work boots             Shoes     3         0.10      rugged                     —       chunky, lug sole

  Hiking boots           Shoes     2         0.10      outdoor                    —       waterproof, lug
                                                                                          sole

  Boat shoes             Shoes     4         0.02      preppy, resort             —

  Espadrilles            Shoes     3         0.02      resort                     —       summer

  Sandals                Shoes     2         0.02      resort, outdoor            —       22 °C and up

  Slides                 Shoes     1         0.01      sporty, resort             —       home, gym or
                                                                                          pool only

Accessories

  Subcategory              Slot        Formality Clo      Style tags              Wash    Notes
                                                                                  after

  Leather belt             Belt        6           —      classic, smart          —       leather color
                                                          casual

  Casual belt (canvas,     Belt        3           —      rugged, streetwear      —
  braided)

  Dress watch              Watch       8           —      classic                 —       metal tone

  Subcategory            Slot      Formality Clo    Style tags            Wash    Notes
                                                                          after

  Everyday watch         Watch     5         —      classic, sporty,      —       metal tone
  (diver, field)                                    minimal

  Sport, digital or      Watch     2         —      sporty                —
  smartwatch

  Backpack               Bag       2         —      streetwear, sporty,   —
                                                    minimal

  Tote                   Bag       3         —      minimal               —

  Crossbody / sling      Bag       2         —      streetwear            —

  Leather briefcase /    Bag       7         —      classic               —       leather color
  messenger

  Cap                    Headwear 2          —      streetwear, sporty    —       sun

  Beanie                 Headwear 2          0.03   streetwear,           —       cold
                                                    minimal, rugged

  Bucket hat             Headwear 2          —      streetwear            —

  Sunglasses             Eyewear   2–8       —      any                   —       frame metal or
                                                                                  acetate color

  Scarf                  Neck      5         0.05   classic, minimal      —       wind, cold

  Tie                    Neck      9         —      classic               —       needs a collar and
                                                                                  a blazer

  Pocket square          Pocket    8         —      classic               —       needs a blazer

  Necklace / chain       Jewelry   3         —      streetwear            —       metal

  Bracelet               Jewelry   3         —      any                   —       metal or leather

  Ring                   Jewelry   4         —      any                   —       metal

  Gloves                 Hands     —         0.05   any                   —       5 °C and below
```

## Appendix B — Color and pairing tables

```text
The engine loads these tables as data; change a number here and the behavior follows.

B1 Color families

   Family               Hex        Neutralness      Temperature

   Black                #111111    1.0              neutral

   Charcoal             #36454F    1.0              cool

   Mid grey             #808080    1.0              neutral

   Family              Hex       Neutralness   Temperature

   Light grey          #C8C8C8   1.0           neutral

   White               #FAFAFA   1.0           cool

   Ecru / cream        #EFE6D2   0.95          warm

   Stone / beige       #D6C7A8   0.9           warm

   Khaki               #C3B091   0.85          warm

   Taupe               #8B7D6B   0.9           neutral

   Camel / tan         #C19A6B   0.8           warm

   Brown               #7B4B2A   0.8           warm

   Chocolate           #4A2C1D   0.85          warm

   Olive               #6B6B3A   0.7           warm

   Navy                #1F2A44   0.9           cool

   Dark denim          #2E3F5C   0.9           cool

   Mid denim           #5B7FA6   0.85          cool

   Light denim         #A7BCD6   0.8           cool

   Light blue          #A9C7E8   0.6           cool

   Cobalt              #2F55D4   0.0           cool

   Teal                #1F7A7A   0.2           cool

   Forest green        #1F4D2E   0.4           cool

   Sage                #9CAF88   0.5           neutral

   Kelly green         #2E9E4F   0.0           cool

   Mint                #B8E3C9   0.2           cool

   Burgundy            #6D1A2A   0.4           warm

   Red                 #C62828   0.0           warm

   Rust / terracotta   #B5532E   0.3           warm

   Orange              #EF7D22   0.0           warm

   Mustard             #D4A017   0.2           warm

   Yellow              #F5D33A   0.0           warm

   Family              Hex              Neutralness   Temperature

   Dusty pink          #D8A7A0          0.4           warm

   Pink                #F2A7B8          0.1           cool

   Lavender            #C3B1E1          0.2           cool

   Plum                #5B2A5C          0.3           cool

   Multicolor print    —                0.0           from its clusters

B2 Classic pairing affinities (rule C7)
Unlisted neutral pairs default to 0.75; unlisted pairs involving a chromatic color use the
color math alone. Pairs marked "left to your swipes" are contested, so learning moves
them fastest.

   Pair                      Affinity

   Navy + camel              0.95

   Navy + white or ecru      0.95

   Navy + grey               0.90

   Navy + light blue         0.90

   Navy + stone or khaki     0.90

   Navy + brown              0.90

   Navy + burgundy           0.85

   Navy + olive              0.80

   Navy + black              0.50, left to your swipes

   Black + white             0.95

   Black + grey              0.85

   Black + camel             0.85

   Black + olive             0.70

   Black + brown             0.35, left to your swipes

   Grey + white              0.90

   Grey + burgundy           0.85

   Pair                     Affinity

   Grey + brown             0.80

   Charcoal + light blue    0.90

   Stone + white            0.90

   Stone + brown            0.85

   Stone + olive            0.85

   Olive + ecru             0.85

   Olive + rust             0.85

   Olive + brown            0.85

   Olive + grey             0.70

   Camel + white            0.90

   Camel + grey             0.90

   Brown + light blue       0.90

   Brown + ecru             0.90

   Any denim + white        0.95

   Any denim + camel        0.90

   Any denim + grey         0.85

   Any denim + olive        0.80

   Any denim + black        0.75

   Any denim + navy         0.70

B3 Slot area weights (rules C3, C4)
Weights are normalized to 1 for each outfit.

   Slot                            Area weight

   Outer layer, closed             0.40

   Outer layer, worn open          0.25

   Top alone                       0.30

   Slot                             Area weight

   Top under an open outer          0.15

   Top under a closed outer         0.08

   Mid layer                        0.25 (0.10 under a closed outer)

   Bottom                           0.30

   Shoes                            0.07

   Bag                              0.05

   Scarf                            0.05

   Headwear                         0.03

   Belt                             0.02

   Watch, jewelry, sunglasses       0.01 each

B4 Shoe × trouser table (rule SH2)
✓ = 1.0, ~ = 0.5, ✗ = 0.0 (a heavy soft penalty, not a hard block). Oxfords with jeans are ✗
even though derbies and brogues are ~.

  Shoes / Trousers       Jeans Chinos Tailored or    Linen   Cargo, joggers,   Shorts           Athletic or
                                      suit                   sweats                             swim

  Minimal sneakers       ✓      ✓          ~         ✓       ✓                 ✓                ~

  Chunky or running      ✓      ~          ✗         ~       ✓                 ✓                ✓

  Canvas sneakers        ✓      ✓          ✗         ✓       ✓                 ✓                ~

  Loafers                ✓      ✓          ✓         ✓       ✗                 ✓                ✗

  Derbies, oxfords,      ~      ✓          ✓         ~       ✗                 ✗                ✗
  brogues, monks

  Chelsea, chukka        ✓      ✓          ✓         ✗       ~                 ✗                ✗

  Work or hiking boots   ✓      ~          ✗         ✗       ✓                 ✗ (hiking ✓ in   ✗
                                                                               Hiking)

  Sandals, slides,       ~      ~          ✗         ✓       ✗                 ✓                ✓
  espadrilles

  Boat shoes             ✓      ✓          ✗         ✓       ✗                 ✓                ✗

B5 Style compatibility matrix (rule O1)
Symmetric, 1.0 on the diagonal; learned style-mix features adjust it.

  Style          Minimal   Smart casual   Classic     Streetwear    Sporty   Rugged   Outdoor   Preppy   Resort   Techwear

  Minimal        1.0       0.9            0.8         0.8           0.6      0.6      0.5       0.6      0.7      0.7

  Smart casual   0.9       1.0            0.9         0.6           0.4      0.7      0.4       0.9      0.8      0.4

  Classic        0.8       0.9            1.0         0.4           0.2      0.6      0.3       0.9      0.7      0.2

  Streetwear     0.8       0.6            0.4         1.0           0.9      0.6      0.7       0.4      0.5      0.8

  Sporty         0.6       0.4            0.2         0.9           1.0      0.4      0.8       0.5      0.5      0.8

  Rugged         0.6       0.7            0.6         0.6           0.4      1.0      0.8       0.5      0.4      0.5

  Outdoor        0.5       0.4            0.3         0.7           0.8      0.8      1.0       0.3      0.3      0.8

  Preppy         0.6       0.9            0.9         0.4           0.5      0.5      0.3       1.0      0.7      0.2

  Resort         0.7       0.8            0.7         0.5           0.5      0.4      0.3       0.7      1.0      0.2

  Techwear       0.7       0.4            0.2         0.8           0.8      0.5      0.8       0.2      0.2      1.0
```

## Appendix C — Scenarios, warmth targets, archetypes, explanation copy

```text
These tables set the context the engine works in and the words it explains itself with.

C1 Scenario presets

  Scenario         Formality     Window         Indoor      Activity      Palette           Accessories Notes
                   min–                         share
                   target–
                   max

  Everyday         1–3–5         10:00–         0.5         light         any               0–3
  casual                         20:00

  Scenario     Formality   Window       Indoor   Activity   Palette        Accessories Notes
               min–                     share
               target–
               max

  University   2–3.5–5     08:00–       0.7      light      any            1–3        backpack
                           18:00                                                      bonus;
                                                                                      comfortable
                                                                                      shoes for
                                                                                      walking
                                                                                      between
                                                                                      buildings; a
                                                                                      light or
                                                                                      removable
                                                                                      layer for cold
                                                                                      lecture halls
                                                                                      and warm
                                                                                      commutes;
                                                                                      sneakers,
                                                                                      chinos, jeans,
                                                                                      hoodies and
                                                                                      overshirts all
                                                                                      fine; no tie or
                                                                                      suit

  Office       4–6–7       08:00–       0.85     sedentary any,            1–3        collared top
  (business                19:00                           conservative-              bonus
  casual)                                                  leaning

  Business     7–8.5–10    08:00–       0.85     sedentary conservative    2–4        suit or blazer
  formal                   19:00                                                      required

  Interview    6–7.5–9     2 h either   0.9      sedentary conservative    1–3        no loud
                           side of                                                    patterns or
                           the start                                                  logos

  Brunch /     3–4–6       10:00–       0.5      light      any            1–3
  coffee                   14:00

  Date night   4–5.5–7     19:00–       0.7      light      evening        1–3
                           24:00

  Dinner       5–6.5–8     19:00–       0.85     sedentary evening         2–4
  (smart)                  24:00

  Night out    3–5–7       21:00–       0.8      active     evening        1–4        bolder allowed
                           03:00

  Concert /    1–3–5       18:00–       0.3      active     any            1–3        comfortable
  festival                 02:00                                                      shoes

  Wedding      7–8.5–10    12:00–       0.6      light      any, no        2–4
  guest                    24:00                            dominant
                                                            white

  Scenario       Formality     Window    Indoor    Activity    Palette           Accessories Notes
                 min–                    share
                 target–
                 max

  Funeral /      7–8–10        09:00–    0.6       sedentary dark muted          1–2
  memorial                     15:00

  Religious      5–6.5–8       09:00–    0.9       sedentary conservative        1–3
  service                      13:00

  Sports         1–3–4         event     0.2       sedentary any                 1–3        warmer: sitting
  event                        time                                                         outdoors
  (spectator)

  Travel day     2–3–5         whole     0.6       sedentary any                 1–3        comfort,
                               day                                                          pockets, a
                                                                                            removable
                                                                                            layer

  Gym /          1–1–2         set by    1.0       active      any               0–2        sporty tags
  training                     you                                                          required

  Running        1–1–2         set by    0.0       active      any               0–2        technical
                               you                                                          fabrics

  Hiking /       1–2–3         08:00–    0.0       active      any               1–3        boots, layers,
  outdoors                     17:00                                                        rain shell

  Beach / pool   1–1–3         10:00–    0.0       light       light             0–3        swim shorts
                               19:00                                                        allowed

  Home /         1–1–2         all day   1.0       sedentary any                 0          rules relaxed,
  lounge                                                                                    no shoes
                                                                                            needed

C2 Warmth targets and slot structures (rule W1, generator step 2)
Outdoor targets assume walking with outerwear on; activity shifts them (sedentary +0.2
clo, active −0.2) and your learned thermal offset moves the temperature axis. Indoor
comfort is checked separately (SPEC 5.8).

   Feels-like (°C)       Target I_cl outdoors     Typical structures

   28 and above          0.30–0.40                top + bottom (shorts OK) + shoes

   24–28                 0.40–0.50                top + bottom + shoes

   20–24                 0.45–0.65                top + bottom + shoes; top + open light outer

   16–20                 0.65–0.85                top + mid; top + light outer

   12–16                 0.85–1.05                top + mid + light outer; top + outer

   Feels-like (°C)   Target I_cl outdoors       Typical structures

   8–12              1.00–1.20                  top + mid + outer

   4–8               1.20–1.45                  top + mid + warm outer + scarf

   0–4               1.40–1.65                  top + mid + warm outer + scarf + beanie

   Below 0           1.60–2.00                  as above + gloves

Sanity check: tee, merino crewneck, field jacket, jeans and chelsea boots sum to 1.02 clo,
which the ISO formula turns into an ensemble of about 1.0 — the 8–12 °C band.

C3 Gap-finder archetypes

   Archetype                     Typical colors

   Oxford shirt                  white, light blue

   Plain tee                     white, black, grey, navy

   Merino crewneck               navy, grey, camel, olive

   Hoodie                        grey, black, navy

   Overshirt                     olive, navy, ecru

   Unstructured blazer           navy

   Denim jacket                  mid denim

   Bomber                        black, olive, navy

   Chore jacket                  navy, olive, ecru

   Wool overcoat                 camel, charcoal, navy

   Rain shell                    black, navy, olive

   Puffer jacket                 black, olive

   Jeans                         dark denim, black

   Chinos                        stone, navy, olive

   Wool trousers                 mid grey, charcoal

   Joggers                       grey, black

   Chino shorts                  navy, stone

   Minimal leather sneakers      white

   Archetype               Typical colors

   Chelsea boots           dark brown suede, black leather

   Loafers                 brown

   Derbies                 black, dark brown

   Running shoes           grey, black

   Leather belt            brown, black

   Everyday watch          silver

   Backpack                black

   Cap                     navy

   Beanie                  grey, black

   Scarf                   grey, camel

   Sunglasses              black, tortoise

C4 Explanation copy
Templates live in strings.xml ; arguments come from the rule result.

   Key                              Template

   HARMONY_NEUTRAL                  All neutrals: easy to wear, hard to get wrong.

   HARMONY_ANALOGOUS                {a} and {b} sit side by side on the color wheel: calm
                                    and cohesive.

   HARMONY_COMPLEMENTARY            {a} against {b}: opposite colors, kept in check by
                                    neutrals.

   NEUTRAL_ACCENT                   Neutral base with one {accent} accent (about {pct}
                                    %).

   VALUE_CONTRAST                   A lighter top over darker trousers grounds the look.

   LEATHER_MATCH                    Belt and shoes in the same {color} leather.

   LEATHER_CLASH                    {belt} belt with {shoes} shoes: swap one so they
                                    match.

   FORMALITY_FIT                    Formality {f}/10 fits {scenario} ({min}–{max}).

   Key                              Template

   WEATHER_LAYER                    Feels {min} → {max} °C: the {item} comes off by
                                    midday.

   RAIN                             {pct} % chance of rain: water-resistant {item}, no
                                    suede.

   ROTATION                         You wore the {item} {n} days ago.

   LEARNED_LIKE                     You've liked {a} with {b} {n} times.

   SILHOUETTE                       A relaxed top balanced by slimmer trousers.

   PATTERN_ONE                      One pattern, everything else solid.

   SHOE_REST                        Your {shoes} need a day off after yesterday.
```

## Sources

```text
     Claude Code — Advanced setup: native Windows install, Git for Windows vs
     PowerShell.
     Claude Code — Common workflows: plan mode with Shift+Tab, pasting images with
     Alt+V on Windows.
     ML Kit Subject Segmentation for Android: beta status, unbundled model, about 200 KB,
     manifest meta-data.
     Open-Meteo Terms & Privacy: free non-commercial use under 10,000 calls a day (5,000
     an hour, 600 a minute), CC BY 4.0 data licence, what counts as commercial.
     Google Play — App testing requirements for new personal developer accounts: 12
     testers, 14 continuous days, accounts created after 13 November 2023.

Not looked up for this doc, so verify before relying on exact numbers: the hue-template
sizes (Matsuda's templates as used in Cohen-Or et al., "Color Harmonization", SIGGRAPH
2006), the clo values and summation formula (ISO 9920), and the OKLab matrices (Björn
Ottosson). Claude Code should confirm each against its primary source when
implementing.
```
