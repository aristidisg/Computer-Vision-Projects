# Ensemble

A menswear outfit app for Android that suggests looks from photos of **your own clothes** —
explained with real style rules and tuned by counting your swipes. No AI in the
recommendations, no accounts, no ads; everything stays on your phone.

## Install on your phone

1. On your Android phone, open this repository's **Releases** page
   (`https://github.com/aristidisg/Computer-Vision-Projects/releases`).
2. Download **`ensemble.apk`** from `latest` (main) or `preview` (newest branch build).
3. Open it and allow your browser to install apps when Android asks. Done.

New builds install as updates over the old one, so your wardrobe, outfits and learned taste are
kept. Data also backs up with Android's Auto Backup, and **You → Your data** can export a zip or
write a weekly backup into a folder you choose (Google Drive works).

## What it does

- **Today** — a swipe deck of outfits for the day's scenario and forecast. Right = like,
  left = no (with "why not?" chips), up = wearing it. Each card shows Match %, the color
  harmony, and why it works.
- **Wardrobe** — add pieces with the camera, gallery or share sheet. Automatic cutout, white
  balance and color extraction; the category fills in every other tag.
- **Outfits** — saved and composed outfits, a composer with live scoring, inspiration photos
  rebuilt from your clothes, and a wear history.
- **Plan** — an auto-planned week that respects rotation and laundry, trip packing, laundry.
- **You** — style profile, taste profile, rules and weights, custom rules, scenarios, personal
  color, insights and gap finder, notifications, data.

## How it's built

Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, WorkManager, Glance, Coil, ML Kit
(on-device cutouts and category hints only). The outfit engine is pure Kotlin
(`:engine`) — color science in OKLab, ~60 rules, beam-search generator, counted learning — and
is fully unit-tested, including the spec's 18 golden cases.

| Module | Contents |
|---|---|
| `:core:model` | Domain types |
| `:engine` | Color science, taxonomy and tables, rules, scoring, generator, learning, planners |
| `:data` | Room, DataStore, Open-Meteo client, image storage, backup |
| `:app` | Compose UI, photo pipeline, workers, widget |

GitHub Actions runs the tests and lint and publishes the signed APK on every push.

- Spec: [docs/SPEC.md](docs/SPEC.md)
- Progress: [PROGRESS.md](PROGRESS.md) · Decisions: [DECISIONS.md](DECISIONS.md)
- Notes for Claude Code: [CLAUDE.md](CLAUDE.md)

Weather data by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0).
