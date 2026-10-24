# Ensemble — project memory for Claude Code

## What this is
Android app (Kotlin, Jetpack Compose) that suggests menswear outfits from the user's own
photographed wardrobe using deterministic rules, tags and counted feedback.
Spec: docs/SPEC.md. Progress: PROGRESS.md. Decisions: DECISIONS.md.

## How this repo is built (cloud, not Windows)
- There is no local Android SDK in Claude Code cloud sessions. GitHub Actions
  (.github/workflows/android.yml) builds and tests everything and publishes the APK to GitHub
  Releases: `latest` from main, `preview` from other branches. The user installs from there.
- `:core:model` and `:engine` are pure Kotlin and can be compiled and tested anywhere a JDK is
  available. In a cloud container where Gradle can't reach Google's Maven, compile them with a
  standalone kotlinc as two separate compilations (model, then engine against it) so
  cross-module smart-cast errors show up, and run the tests with the JUnit console launcher.
- Every build is signed with app/keystore/ensemble.jks and versionCode = GITHUB_RUN_NUMBER so
  updates install over the previous version and keep the user's data. Never change the
  applicationId or the signing key: the user would lose their wardrobe.

## Non-negotiables
1. No AI in recommendations: no LLM or AI API calls, no TensorFlow Lite or other ML models,
   no cloud "smart" services in :engine or anywhere in suggestion logic.
   The only ML allowed: Google ML Kit on-device subject segmentation (cutouts) and
   image labeling (category hints), both in the photo pipeline, both behind a
   settings toggle, outputs always confirmed by the user.
2. :engine is pure Kotlin/JVM: no Android imports, no I/O, no system clock (time is
   passed in), no global randomness (a seeded RNG is passed in).
   Same inputs -> same outputs.
3. Every rule, scorer, planner and learning update has unit tests showing it firing
   and not firing. No rule ships untested.
4. Rules, weights, taxonomy defaults, color families and pairing tables are data
   (Kotlin objects or JSON assets), never scattered if-statements.
5. Offline-first. Network only for Open-Meteo forecast + geocoding.
   No analytics, no accounts, no ads. User data leaves the device only via export
   (and Android's own Auto Backup / the weekly backup folder the user picks).
6. Data persists across updates: every Room schema change ships with a migration in
   EnsembleDatabase.MIGRATIONS and a migration test. Never use destructive migration.

## Architecture
- Modules: :app (UI, DI, photo pipeline, workers, widget), :core:model (pure Kotlin
  types), :engine (color science, rules, scoring, generator, learning, planners),
  :data (Room, DataStore, repositories, weather client, image files, backup).
- Dependencies: app -> data, engine, model; data -> model, engine; engine -> model.
  Nothing depends on :app. :engine never depends on :data.
- UI: single activity, Compose + Material 3, unidirectional data flow. ViewModels
  expose StateFlow and take events. No business logic in composables.
- DI: Hilt (KSP). Async: coroutines + Flow. Engine work runs on Dispatchers.Default.
- Room stores each record as a JSON document plus indexed columns, with exported
  schemas in data/schemas. Settings are one JSON document in DataStore.
- All user-facing text in strings.xml (English now, translatable later).

## Commands
- Engine tests (fast): ./gradlew :engine:test
- All unit tests: ./gradlew test
- Build: ./gradlew assembleRelease
- Lint: ./gradlew :app:lintRelease

## Definition of done (every task)
- CI is green: :engine:test, testDebugUnitTest, lintRelease and assembleRelease pass.
- New logic has tests; new screens have @Preview where practical.
- PROGRESS.md updated (done, next, known issues); choices logged in DECISIONS.md.
- One focused commit (feat:, fix:, test:, refactor:, chore:).

## Working rules
- Read PROGRESS.md first.
- Explain any new dependency before adding it; pin versions in gradle/libs.versions.toml.
- Spec ambiguous -> ask. Forced to choose -> pick the simplest option and log it.
- Small files (under 300 lines), sealed types and enums over strings, no !!.
- Keep the "Seed demo wardrobe" action working: it is how features get tested without photos.
- Never weaken a test to make it pass; fix the code or ask.
