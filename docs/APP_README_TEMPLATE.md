<!--
README template for apps of the GB family. Copy it to the app repository as README.md and replace every <...>.
Keep the section order; drop a row or section only when the app really doesn't have it.
-->
<div align="center">

# <AppName>

**<Console> emulation for Android phones and handhelds**

<Top 3-4 features separated by ·>

[Download the latest APK](../../releases/latest) · [Changelog](CHANGELOG.md) · [Privacy policy](PRIVACY.md)

<img src="docs/screenshots/library.png" width="760" alt="Game library with real game titles and cover art">

</div>

## Highlights

| | |
| --- | --- |
| 🚀 **<Renderer>** | <What makes rendering good: GPU renderer, resolution, fallbacks> |
| 🎮 **Built for handhelds** | Full controller navigation, remapping on a drawing of the console's controller, touch controls |
| 💾 **Save states** | Slots with screenshots, auto save/load, "Resume" view with the latest state of each game |
| 🏆 **RetroAchievements** | Login, hardcore mode, notifications, achievement list |
| 🎯 **Cheats** | Per-game cheat list, custom codes and file import; core-specific code formats and databases where available |
| 🖼️ **Game library** | Box art from libretro-thumbnails, custom covers, play time |
| ⚡ **Quick presets** | High / Mid / Low in the setup wizard |

## Screenshots

Use real game titles and actual cover art. Include a library screenshot and two or more useful app screens (for example gameplay, quick menu, or settings). Store README captures in `docs/screenshots/` and link each image with an accurate caption. Do not create fictional titles or placeholder covers for screenshots. Fixtures may create empty disc files to populate the library, but must never include or load copyrighted game data.

<p align="center">
  <img src="docs/screenshots/gameplay.png" width="32%" alt="Gameplay in <AppName>">
  <img src="docs/screenshots/settings.png" width="32%" alt="<AppName> settings">
  <img src="docs/screenshots/quick-menu.png" width="32%" alt="<AppName> in-game quick menu">
</p>

If a sample screen is not available, remove its image instead of adding a mockup or placeholder.

## All features

- **Emulation:** <core and supported consoles/platforms>
- **Video:** <renderers, enhanced resolution, display modes, filters, shaders>
- **Audio and performance:** <audio options, frame pacing/skipping and profiles>
- **Input:** <physical controllers, remapping, touch controls and hotkeys>
- **Saves and library:** <save states, storage, covers, layouts and play time>
- **Online:** <achievements, network features, and optional purchases; omit unsupported items>

**In-game quick menu:** follow the shared [GBFrame quick-menu standard](https://github.com/mindspotgb/GBFrame/blob/main/docs/QUICK_MENU_STANDARD.md), based on GBSat. Keep the standard layout and navigation; list only options that the app/core really supports.

**Cheats and achievements:** reuse the GBFrame `CheatsScreen`/`CheatsBackend` and `AchievementsScreen`/`RaBackend` components. Document any core-specific limitation; do not fork the shared UI.

## Requirements

- Android <min> or newer, 64-bit ARM (arm64-v8a)
- <BIOS / firmware the user must provide>. **None is included.**
- Your own game images. **No games are included or downloaded.**

## Getting started

1. Install the APK from the [Releases](../../releases/latest) page.
2. Follow the setup wizard: <BIOS>, games folder (no storage permission needed), preset.
3. Choose a game in the library.

## Downloads and versions

A release (`<AppName>-v<version>.apk`) is published automatically when `apps/android/VERSION` changes; its notes come
from [CHANGELOG.md](CHANGELOG.md). Development builds of every push are in the **Actions** tab. All APKs are signed
with the same key, so updates install over each other.

## Building

Android package convention: set both Gradle `namespace` and `applicationId` to the same `app.<app-slug>.emulator` value. Source-code package declarations may differ; in that case use fully qualified component class names in the manifest and keep JNI entry points aligned with the actual source package.

| Path | Contents |
| --- | --- |
| `apps/android` | Android app: Kotlin/Compose UI and the JNI bridge |
| `<core path>` | Emulator core |
| `libs/gbframe` | Shared framework ([GBFrame](https://github.com/mindspotgb/GBFrame), git submodule) |
| `.github/workflows` | APK build, profiling and PGO training builds, screenshots |

<Local build requirements: SDK 36, NDK version, CMake version, extra tools.>

## Privacy

<AppName> collects no personal data; see the [privacy policy](PRIVACY.md).

## License

<AppName> is based on [<Core>](<core URL>) by <authors> (<core license>).
<Console> is a trademark of <maker>. <AppName> is not affiliated with or endorsed by <maker>.

GNU GPL version 2 or (at your option) any later version (GPL-2.0-or-later). See [LICENSE](LICENSE).
