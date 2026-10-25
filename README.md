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
| `PerfOverlay.kt` | Monitoring overlay (FPS, speed, CPU/GPU, renderer, resolution, frame time): FPS and speed green from 95% of full speed, yellow from 80%, red below (`speedColor`); GPU usage from the app's own GPU time (`gpuLoad`: timer/timestamp queries), else the driver's sysfs load |
| `Controller.kt` | `rememberHasGamepad()` |
| `Wizard.kt` | First-run `SetupWizard` (steps with gradient badges), `WizardStatus`, `PresetPicker` |
| `Covers.kt` | Box art: custom image → cache → libretro-thumbnails download with fuzzy title matching (`Covers.init(dataDir, system = "Sega - Saturn")`) |
| `RetroAchievements.kt` | RetroAchievements client logic (login, hardcore, notifications, per-game achievement lists); the app implements `RaBackend` with its native rcheevos client |
| `RaOffline.kt` | Offline achievements (casual only): caches login/game/session answers while online, queues offline unlocks and syncs them later with the real unlock time |
| `PlayTime.kt` | Play time (`PlayTime.update(game, counting)`), `formatPlayTime` |
| `SaveData.kt` | Game-named save folders (`gameFolderName`), Export (saves + save states) / Import |
| `ResumeEntry.kt` | Save-state entry model (`ResumeEntry`) used by the All in One layout; there is no separate Recent library mode |
| `Home.kt` | Home screen: `HomeTopBar` (icon, label, clock, battery) and `HomeLibrary` in three layouts (Showcase, Grid, All in One). The main menu's LIBRARY tiles cycle Theme (Showcase/Grid/All in One) and toggle Fullscreen; there is no separate All games/Recent mode. `HomeThemeSettings` remains as an empty compatibility entry point. `HomeMenuPanel` main menu (library view, settings tiles, quit); `HomeTheme` remembers the selected theme; `CoverCard` box art |
| `LibraryView.kt` | Last selected game/save state remembered between sessions (`LibraryView.init(prefs)`, `lastGame`, `lastState`) |
| `DeviceTier.kt` | Device class from CPU core types and the GPU: Entry (little cores only, RK3566), Low (≤2 fast cores or A73-class cores: T618, Snapdragon G1 Gen 2, 662), Mid (modest GPU, T820), High (strong GPU: Snapdragon 865+, Dimensity 8300); picks the recommended preset |
| `GameProfiles.kt` | Recommended settings per game and device class shipped in `assets/game_profiles.json` (user choice > database > global settings) |
| `PadDiagram.kt` | Controller diagram for button mapping (`PadLayout`, `PadButton`, `PadDiagram`) |
| `AchievementsUi.kt` | Unlock notifications, achievement list, `RetroAchievementsSettings`, `RaUserHeader` |

Also shared beyond Kotlin:

| Path | Contents |
| --- | --- |
| `gbframe/.../Hotkeys.kt` | Hotkeys (quick save/load, fast forward, menu) with button combinations, `keyName` |
| `gbframe/.../SafGames.kt` | Games folder through the Storage Access Framework: scan, open discs with their track files (CUE, GDI, MDS) as `/proc/self/fd/N` symlinks for path-based cores |
| `native/` | C++ for the app's JNI library (`gb_native.cmake`): OpenGL ES presenter (`gl_renderer`, zero-copy AHardwareBuffer, filters, shaders: Scanlines, CRT, CRT Royale, GTU, LCD3x; bsnes gamma ramp colour correction; GPU time of its draws for the overlay), CPU placement + performance hints (`cpu_tuning`, thread roles by name), PGO training support (`pgo`) |
| `native/auto_skip` | Skip Engine (`gb::AutoSkip`): maximum speed through loading screens, black transitions and save waits, and whether a faster disc read speed is safe (not while the game streams video/music from the disc). The app reports its core's disc and backup memory activity, delivered frames and sound samples |
| `native/diag_props.hpp` | A/B diagnostic switches read from adb-set properties (`debug.<app>.<name>`, `gb::DiagOn`), shown in the performance log |
| `Shaders.kt` | The presenter's shader and colour correction ids, names and menu order (`Shaders.menuOrder`, `name`, `next`, `sanitize`) |
| `tools/emu-test.bat` | One Windows menu for device tests over USB (copy it to the app and set the block at the top): download/install the latest build, performance log, CPU profile (simpleperf with the profiling build, generated when older than the normal build), library scroll measurement, PGO data, A/B diagnostic switches, wake the device, screen capture. Output files are named after the game |

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

Use the following Android package identity convention for every new GB-family app:

- Set the app's **`applicationId`** to `app.<app-slug>.emulator`, all lowercase. Use the stable product slug, not a console name that may later change. Examples: GBSat → `app.gbsat.emulator`; GBCast → `app.gbcast.emulator`.
- Describe the app with the standard label **`<Manufacturer> <Console> Emulator for Android`**, e.g. `Sega Saturn Emulator for Android` (`emulatorLabel(manufacturer, console)`). The kit's `AboutPage(appName, manufacturer, console, icon, credits, license)` (Settings → About) shows it under the app name, followed by the version and the **Application id**, then Credits and License; apps do not build their own About page.
- Keep **`namespace`** and the source-code package separate from `applicationId`. They may remain app-specific implementation namespaces (for example, `app.gbsat.android`); do not globally rename Java/Kotlin packages just to match the install ID.
- Before changing an ID after public distribution, remember that Android treats it as a different app: it won't update the old package in place or automatically migrate its private data. Choose the ID before the first release.
- Audit manifest authorities, deep links, backup rules and any hard-coded package references when choosing or changing the ID. Prefer authorities derived from `${applicationId}` when appropriate.

```kotlin
// apps/android/app/build.gradle.kts (example)
android {
    namespace = "app.gbsat.android" // implementation namespace
    defaultConfig {
        applicationId = "app.gbsat.emulator" // stable Android app identity
    }
}
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

Inside the theme, components read their colors from `palette` (e.g. `palette.accent`, `palette.backgroundBrush`).

## Documentation for apps

- [docs/LESSONS.md](docs/LESSONS.md): lessons learned (performance, GPU renderers, controller, storage, Play, CI).
- [docs/QUICK_MENU_STANDARD.md](docs/QUICK_MENU_STANDARD.md): mandatory GBSat-based in-game quick-menu layout, options, navigation and focus behavior.
- [docs/APP_README_TEMPLATE.md](docs/APP_README_TEMPLATE.md): every app has its own README, written from this template (same sections in every app).

## Rules

- Only emulator-independent code lives here: no core, JNI or console-specific options.
- **All new GB-family Android apps must use the `app.<app-slug>.emulator` applicationId convention** documented above.
- **Every GB-family Android app must follow the [GBSat-based quick-menu standard](docs/QUICK_MENU_STANDARD.md)** for layout, item hierarchy, submenu/back behavior and controller focus. Options are capability-aware: never expose inert or unsupported controls.
- A change here reaches every app when it updates the submodule (`git submodule update --remote libs/gbframe`).

## License

GPLv3 (see [LICENSE](LICENSE)). Apps using this kit are distributed under GPLv3 too, so their cores must be GPLv3-compatible (e.g. GPLv2 "or later", GPLv3, MIT, BSD, Apache 2.0).
