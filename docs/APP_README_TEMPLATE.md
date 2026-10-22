<!--
README template for apps of the GB family. Copy it to the app repository as README.md and replace every <...>.
Keep the section order; drop a row or section only when the app really doesn't have it.
-->
<div align="center">

# <AppName>

**<Console> emulation for Android phones and handhelds**

<Top 3-4 features separated by ·>

[Download the latest APK](../../releases/latest) · [Changelog](CHANGELOG.md)

<img src="docs/screenshots/library.png" width="760" alt="Game library">

</div>

## Highlights

| | |
| --- | --- |
| 🚀 **<Renderer>** | <What makes rendering good: GPU renderer, resolution, fallbacks> |
| 🎮 **Built for handhelds** | Full controller navigation, remapping on a drawing of the console's controller, touch controls |
| 💾 **Save states** | Slots with screenshots, auto save/load, "Resume" view with the latest state of each game |
| 🏆 **RetroAchievements** | Login, hardcore mode, notifications, achievement list |
| 🖼️ **Game library** | Box art from libretro-thumbnails, custom covers, play time |
| ⚡ **Quick presets** | High / Mid / Low in the setup wizard |

## Screenshots

<Screenshots rendered from code by the screenshots workflow (docs/screenshots), sample covers only.>

## All features

- <Feature list by area: emulation, video, audio, input, saves, library, settings>

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

| Path | Contents |
| --- | --- |
| `apps/android` | Android app: Kotlin/Compose UI and the JNI bridge |
| `<core path>` | Emulator core |
| `libs/gbframe` | Shared framework ([GBFrame](https://github.com/mindspot7/GBFrame), git submodule) |
| `.github/workflows` | APK build, profiling and PGO training builds, screenshots |

<Local build requirements: SDK 36, NDK version, CMake version, extra tools.>

## Privacy

<AppName> collects no personal data; see the [privacy policy](PRIVACY.md).

## License

<AppName> is based on [<Core>](<core URL>) by <authors> (<core license>).
<Console> is a trademark of <maker>. <AppName> is not affiliated with or endorsed by <maker>.

GPLv3. See [LICENSE](LICENSE).
