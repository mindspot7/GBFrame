# GBFrame

Shared Jetpack Compose interface for the GB family of Android emulators (GBSat and future apps): the same screens,
menus and controller behavior in every app, with a different color identity per app.

## What's inside (`gbframe` module, package `gb.frame`)

| File | Contents |
| --- | --- |
| `Theme.kt` | `EmuPalette` (accent, highlight, backgrounds, panels…), `EmuPalettes.Violet` (GBSat), `EmuPalette.fromAccent(color)`, `EmuTheme` (Material colors + automatic down-scaling on small screens such as 720×720) |
| `Settings.kt` | Settings page blocks: `SettingsGroup`, `ToggleRow`, `ActionRow`, `ChoiceRow`, `InfoRow`, `SettingsDivider`, `RowIcon` |
| `Dialogs.kt` | `ConfirmDialog` (in-window, works with A/B), `ButtonHint` + `PadColors` |
| `MenuIcons.kt` | Illustrated multicolor menu icons (`MenuIcon`, `MenuIconView`) |
| `PerfOverlay.kt` | Monitoring overlay (FPS, speed, CPU/GPU, renderer, resolution, frame time) |
| `Controller.kt` | `rememberHasGamepad()` |
| `Wizard.kt` | First-run `SetupWizard` (steps with gradient badges), `WizardStatus`, `PresetPicker` |
| `Covers.kt` | Box art: custom image → cache → libretro-thumbnails download with fuzzy title matching (`Covers.init(dataDir, system = "Sega - Saturn")`) |
| `RetroAchievements.kt` | RetroAchievements client logic (login, hardcore, notifications, per-game achievement lists); the app implements `RaBackend` with its native rcheevos client |
| `RaOffline.kt` | Offline achievements (casual only): caches login/game/session answers while online, queues offline unlocks and syncs them later with the real unlock time |
| `PlayTime.kt` | Play time per game (`PlayTime.update(game, counting)`), `formatPlayTime` |
| `SaveData.kt` | Game-named save folders (`gameFolderName`), Export (saves + save states) / Import |
| `ResumeGrid.kt` | "Resume" view: the latest save state of each game with its screenshot, newest first (`ResumeEntry`, `ResumeGrid`; `latestPerGame = false` lists them all) |
| `LibraryView.kt` | Library layout (all games / recent save states) remembered between sessions (`LibraryView.init(prefs)`, `LibraryView.recent`, `toggle()`) |
| `DeviceTier.kt` | Device class from CPU core types and the GPU: Entry (little cores only, RK3566), Low (≤2 fast cores or A73-class cores: T618, Snapdragon G1 Gen 2, 662), Mid (modest GPU, T820), High (strong GPU: Snapdragon 865+, Dimensity 8300); picks the recommended preset |
| `GameProfiles.kt` | Recommended settings per game and device class shipped in `assets/game_profiles.json` (user choice > database > global settings) |
| `PadDiagram.kt` | Controller diagram for button mapping (`PadLayout`, `PadButton`, `PadDiagram`) |
| `AchievementsUi.kt` | Unlock notifications, achievement list, `RetroAchievementsSettings`, `RaUserHeader` |

Also shared beyond Kotlin:

| Path | Contents |
| --- | --- |
| `gbframe/.../Hotkeys.kt` | Hotkeys (quick save/load, fast forward, menu) with button combinations, `keyName` |
| `gbframe/.../SafGames.kt` | Games folder through the Storage Access Framework: scan, open discs with their track files (CUE, GDI, MDS) as `/proc/self/fd` symlinks for path-based cores |
| `native/` | C++ for the app's JNI library (`gb_native.cmake`): OpenGL ES presenter (`gl_renderer`, zero-copy AHardwareBuffer, filters, shaders), CPU placement + performance hints (`cpu_tuning`, thread roles by name), PGO training support (`pgo`) |
| `tools/` | Windows scripts for device tests over USB: `profile.bat` (simpleperf), `perf-log.bat` (performance log), `pgo-pull.bat` (PGO training data); set `APP`/`NAME` at the top |

## Using it in an app

Add this repository as a git submodule and include the module:

```bash
git submodule add https://github.com/mindspot7/GBFrame libs/gbframe
```

```kotlin
// settings.gradle.kts
include(":gbframe")
project(":gbframe").projectDir = file("<path to>/libs/gbframe/gbframe")

// root build.gradle.kts
plugins { id("com.android.library") version "8.10.1" apply false }

// app/build.gradle.kts
dependencies { implementation(project(":gbframe")) }
```

```kotlin
// Box art and RetroAchievements (once, at startup)
Covers.init(dataDir, system = "Sony - PlayStation")
RetroAchievements.init(context, appName = "MyEmu", version = "1.0.0", backend = myRaBackend)
```

```kotlin
// Pick the app's colors once, at the root
EmuTheme(palette = EmuPalette.fromAccent(Color(0xFF6FD3FF))) { /* app */ }
```

Inside the theme, components read the colors from `palette` (e.g. `palette.accent`, `palette.backgroundBrush`).

## Documentation for apps

- [docs/LESSONS.md](docs/LESSONS.md): lessons learned (performance, GPU renderers, controller, storage, Play, CI).
- [docs/APP_README_TEMPLATE.md](docs/APP_README_TEMPLATE.md): every app has its own README, written from this
  template (same sections in every app).

## Rules

- Only emulator-independent code lives here: no core, JNI or console-specific options.
- A change here reaches every app when it updates the submodule (`git submodule update --remote libs/gbframe`).

## License

GPLv3 (see [LICENSE](LICENSE)). Apps using this kit are distributed under GPLv3 too, so their cores must be GPLv3-compatible (e.g. GPLv2 "or later", GPLv3, MIT, BSD, Apache 2.0).
