# UI_GB

Shared Jetpack Compose interface for the GB family of Android emulators (GBSat and future apps): the same screens,
menus and controller behavior in every app, with a different color identity per app.

## What's inside (`uikit` module, package `gb.ui`)

| File | Contents |
| --- | --- |
| `Theme.kt` | `EmuPalette` (accent, highlight, backgrounds, panels…), `EmuPalettes.Violet` (GBSat), `EmuPalette.fromAccent(color)`, `EmuTheme` (Material colors + automatic down-scaling on small screens such as 720×720) |
| `Settings.kt` | Settings page blocks: `SettingsGroup`, `ToggleRow`, `ActionRow`, `ChoiceRow`, `InfoRow`, `SettingsDivider`, `RowIcon` |
| `Dialogs.kt` | `ConfirmDialog` (in-window, works with A/B), `ButtonHint` + `PadColors` |
| `MenuIcons.kt` | Illustrated multicolor menu icons (`MenuIcon`, `MenuIconView`) |
| `PerfOverlay.kt` | Monitoring overlay (FPS, speed, CPU/GPU, renderer, resolution, frame time) |
| `Controller.kt` | `rememberHasGamepad()` |

## Using it in an app

Add this repository as a git submodule and include the module:

```bash
git submodule add https://github.com/mindspot7/UI_GB libs/ui_gb
```

```kotlin
// settings.gradle.kts
include(":uikit")
project(":uikit").projectDir = file("<path to>/libs/ui_gb/uikit")

// root build.gradle.kts
plugins { id("com.android.library") version "8.10.1" apply false }

// app/build.gradle.kts
dependencies { implementation(project(":uikit")) }
```

```kotlin
// Pick the app's colors once, at the root
EmuTheme(palette = EmuPalette.fromAccent(Color(0xFF6FD3FF))) { /* app */ }
```

Inside the theme, components read the colors from `palette` (e.g. `palette.accent`, `palette.backgroundBrush`).

## Rules

- Only emulator-independent code lives here: no core, JNI or console-specific options.
- A change here reaches every app when it updates the submodule (`git submodule update --remote libs/ui_gb`).
