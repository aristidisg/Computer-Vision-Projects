# Progress

Status of the twelve phases in docs/SPEC.md. Built in one pass in the cloud; CI on GitHub
Actions is the build machine (see DECISIONS.md).

## Phase checklist

- [x] **Phase 0 — Foundation.** Modules :app, :core:model, :engine, :data; version catalog;
  Hilt, Room, DataStore, kotlinx.serialization, Coil; achromatic Material 3 theme (Fraunces +
  Inter, light + dark); five tabs. CI builds a signed release APK and publishes it.
- [x] **Phase 1 — Model, taxonomy, color science.** All SPEC 2 types; Appendix A taxonomy and
  Appendix B tables as data; sRGB ↔ OKLab/OKLCH, ΔE_ok, artist hue, neutralness, temperature.
- [x] **Phase 2 — Data layer and demo wardrobe.** Room (JSON documents), DataStore settings,
  repositories, image layout, demo_wardrobe.json (51 items) and "Seed demo wardrobe".
- [x] **Phase 3 — Wardrobe and photo pipeline.** Grid, filters, rainbow sort, search,
  multi-select bulk actions; camera / Photo Picker multi-select / share sheet; EXIF fix and
  strip; gray-world and tap white balance; ML Kit cutout with OKLab region-growing fallback and
  tolerance slider; erase/restore brush; k-means colors; eyedropper; pattern hint; ML Kit
  category hints behind a toggle; smart defaults; duplicate as new color; item detail.
- [x] **Phase 4 — Rules engine and scoring.** Outfit color model, hard constraints, C1–C15,
  P1–P4, M1–M5, F1–F3, O1–O4, silhouette, SH1–SH7, AC1–AC8, W1–W7 with the ISO 9920 clo
  model, R1–R7, custom rules, group weights, strictness presets, Match %, explanations.
  "Rate my outfit" screen (You → Debug tools) with every rule's score.
- [x] **Phase 5 — Weather, location, scenarios.** Open-Meteo forecast + geocoding, Room cache,
  morning refresh, coarse location with saved-city fallback, manual override per day, window
  aggregates, attribution; 20 scenario presets + editor + weekly template.
- [x] **Phase 6 — Generator and Today deck.** Pools, structures, pair cache, beam search, full
  scoring, accessories pass, MMR, exploration, explanations, seeding; swipe deck with stamps,
  haptics, buttons, TalkBack actions, undo (5), outfit detail, swap, lock and regenerate,
  build around, More / Bolder / Safer.
- [x] **Phase 7 — Feedback and learning.** Feature extraction, event log, decayed stats,
  reason routing, context blending, ramped learned score, thermal and formality offsets;
  "why not?" chips, swipe up = wearing it, piece thumbs, evening check-in, taste profile with
  forget / reset (soft, hard) / rebuild.
- [x] **Phase 8 — Outfits, composer, inspiration.** Saved / Composed / Inspiration / History;
  composer with live Match %, one-tap fix and "Complete it"; inspiration import with
  eyedropper; recreate with per-piece match and missing pieces; cooldown badges.
- [x] **Phase 9 — Planner, laundry, trips.** Auto-plan, locks, swap days, conflicts,
  forecast-change replans; laundry basket, thresholds, auto-move setting, cost per wear; trip
  packing with greedy coverage, checklist and day plan; rotation settings.
- [x] **Phase 10 — Insights, gap finder, custom rules, personal color.** Palette, wears,
  unworn 60/90, coverage heatmap, total outfits, gap finder, orphans; IF/THEN rule builder;
  personal color switch, strength, 12 seasons, quiz, favorites / avoid / never, near-face scope.
- [x] **Phase 11 — Polish and release readiness (first pass).** Onboarding, morning
  notification with Wear it / See more, Glance widget, export/import zip with preview,
  weekly backup folder, delete-all with typed confirmation, R8 release build, app icon.
- [x] **Colour accuracy and wardrobe routines.**
  - **Exact colours.** Every colour has an editable code (#fff or #3B2A22). The rules use the
    exact code, and the family name is only a label.
  - **Multicolour pieces.** Up to 6 colours, each with a share slider. Roles (primary,
    secondary, accent) follow the shares.
  - **Dark browns no longer read as navy.** Family matching weights hue twice as much as
    lightness, and white balance is off by default; Auto now averages only the background.
  - **Editor fixes.** Save is always visible, and "Same as…" copies every tag from another
    piece.
  - **Wardrobe routines.** Seasonal swap, a shopping list fed by the gap finder, and a
    "Wear it again" row in Outfits → Saved.
  - **Layout.** Rows that squeezed text into one letter per line now wrap or scroll.
- [x] **Rotation and colour clashes.**
  - **Deck rotation without wear logs.** Each deck remembers the pieces it showed. Pieces shown
    in most of the last 5 decks give way, and a card loses points for every piece already used
    in the same deck.
  - **No forgotten pieces.** The beam search keeps at least one outfit for every eligible
    piece, so pieces with modest pairing scores still reach the deck.
  - **Clash table.** Olive, forest or Kelly green with pink or dusty pink (rose), and bright
    green with red, are known clashes. C7 now weighs them heavily, even when the hue maths
    rates them as complementary.
- [x] **Saved outfits shape suggestions, and a label check.**
  - **"My collection" boost.** Outfits close to the ones you saved or composed (same colour
    pairs, some of the same pieces) get up to +0.2 Match and the line "Close to outfits you
    saved". The saved outfit itself gets no boost.
  - **Off switch.** Outfits → Saved has a switch for this. Off removes the boost, stops saves and
    compositions from teaching taste, and replays the log so past saves stop counting too.
  - **Check labels** (Wardrobe → ⋮) flags tops, mid layers and outer layers that look misfiled:
    - a typed name that says otherwise;
    - materials that say otherwise (knit-only outerwear, leather mid layers);
    - an ML Kit photo hint, when hints are on;
    - layers changed by hand.
    Each flag has a one-tap fix, a picker and "It's right". Every mid and outer layer is also
    listed for a manual pass.
  - **Category picker** now splits Tops from Mid layers and labels Outer layers.
- [x] **Bug sweep.**
  - **Colour names.** Tinted near-blacks (deep navy) are never called black. Denim names are
    used only for denim pieces. A new Blue family covers mid and royal blues. Stored pieces are
    renamed once on update; typed names are kept.
  - **Check labels.** The picker now has an explicit Save and a "Saved: …" confirmation. "It's
    right" is remembered, and fixes apply to the piece as stored now.
  - **Editor.**
    - Changing material, pattern or fabric weight no longer resets hand-set formality or seasons.
    - Colour shares always add up to 100 %.
    - An empty cutout can't erase a piece's colours.
    - Deleting a piece also removes its outfits and plans, and its detail page closes.
  - **Deck.**
    - Rebuilt when pieces are added or deleted elsewhere.
    - Rotation uses earlier days only, so reopening the app doesn't reshuffle today's deck.
    - Undo after Save removes the saved outfit.
    - Flicks count as swipes.
    - The "Why not?" timer restarts for every No.
  - **Outfits.**
    - "Saved" can be tapped to un-save.
    - Swap shows a spinner, then "nothing else fits" when empty.
    - The composer can't put one piece in two slots and marks pieces in the laundry.
    - "Plan for…" confirms.
  - **Plan.** Plans made elsewhere show up immediately; opening Laundry no longer re-plans the
    week.
  - **App.** Rotating the phone no longer re-runs a share or notification action. Seasonal swap
    re-reads pieces before writing. `!!` removed from import, wear logging and C8.

## Tests

- :engine — 113 tests (colour edits and naming, seasonal swap, wear again, shopping list, clashes, rotation, collection influence, label check and hand-set defaults included): color conversions against reference values, artist-hue anchors,
  nearest family for every family, k-means determinism, harmony templates, every rule firing
  and not firing, learning (determinism, decay, reason routing, scenario isolation, ramp,
  rebuild = incremental), generator invariants over seeded random cases, planner, packing,
  gap finder, recreate, weather windows, **all 18 golden cases**, 300-item benchmark
  (~0.3 s on a desktop JVM; limit 1.5 s).
- :data — Robolectric: items round trip, outfits by signature, stats = rebuild, scenario
  overlay, export → delete → import, Open-Meteo parsing from recorded fixtures, settings
  saved before the shopping list existed still load, deck log reads old and new formats.

## Next

- Check on the phone: add a real shirt (cutout, colors, category hints), swipe a deck, wear,
  evening check-in, auto-plan a week, export a backup.
- Commit the generated Room schema (data/schemas/…/1.json) from the CI artifact and add a
  migration test with the first schema change.
- Measure deck time on the phone (target 300 ms for 300 items); add a baseline profile.
- ViewModel tests with Turbine and Compose UI tests for the swipe deck and item editor.
- Play Store steps (SPEC 10): hosted privacy policy, Data safety form, content rating,
  Play App Signing with an upload key in a GitHub secret, store assets, closed test with
  12 testers for 14 days (personal accounts after 13 Nov 2023), Open-Meteo commercial plan if
  the app ever earns money.

## Known issues / simplifications

- Drag-to-swap in the planner is "tap one day, then another" rather than drag and drop.
- The touch-up brush edits the mask at its own resolution and recomputes on stroke end
  (no live preview while drawing).
- Category hints rely on ML Kit's general labels, which are coarse; the subcategory grid is
  always shown underneath.
- Glance widget shows names and color dots, not photos.
- Colours from a photo are only as good as the light: under warm indoor light, type the code
  (or use the eyedropper) for pieces that matter.
- The shopping list is a simple checklist; ticking an entry off doesn't add the piece to the
  wardrobe.
