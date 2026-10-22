# Project conventions (GB emulator family)

These rules apply to every app built on GBFrame, the shared framework (GBSat and the next emulators). Read them before working on any
of these repositories.

## Documentation

- Read [docs/LESSONS.md](docs/LESSONS.md) before starting an app, and add a line whenever an app teaches something new.
- Every app has its own README.md written from [docs/APP_README_TEMPLATE.md](docs/APP_README_TEMPLATE.md); keep it
  up to date with user-visible changes.

## What belongs in the kit

- Anything not specific to one console: UI, settings behavior, storage (SAF), hotkeys, save data, native helpers
  (presenter, CPU tuning, PGO), device-test tools and their docs. Console-specific code (core, JNI bridge, controller
  layouts, widescreen hacks, cheat formats) stays in the app. When an app gains something generic, move it here.

## Working with the owner

- Talk to the owner in **Portuguese (pt-BR)**; code, comments, commit messages and CHANGELOG stay in English.
- The owner tests on real devices: an Adreno 740 handheld (Snapdragon 8 Gen 2) and a Unisoc T618 / Mali-G52 device with
  a 720x720 square screen. Keep both in mind (performance, small screens).
- Devices most users are expected to have (recommended settings go by `DeviceTier`, not by brand; check new layouts on
  these screens, `DeviceScreens` renders them):

  | Device | Chip / GPU | Screen | Tier |
  |---|---|---|---|
  | Anbernic RG DS | Rockchip RK3568 (4x A55) / Mali-G52 2EE | 640x480 4.0" 4:3 | Entry |
  | Anbernic RG Rotate | Unisoc T618 / Mali-G52 | 720x720 3.5" 1:1 | Low |
  | Retroid Pocket Classic | Snapdragon G1 Gen 2 (2 fast cores) / Adreno A12 | 1240x1080 3.92" 5:4 | Low |
  | Mangmi Air X | Snapdragon 662 (4x A73) / Adreno 610 | 1920x1080 5.5" 16:9 | Low |
  | Anbernic RG Cube | Unisoc T820 / Mali-G57 | 720x720 3.95" 1:1 | Mid |
  | Anbernic RG476H | Unisoc T820 / Mali-G57 | 1280x960 4.7" 4:3 | Mid |
  | Anbernic RG477V | Dimensity 8300 / Mali-G615 MC6 | 1280x960 4.7" 4:3 | High (Native 2x) |
  | Retroid Pocket Nova | Snapdragon 8 Gen 2 / Adreno 740 | 1280x960 4.5" 4:3 | High (Native 4x) |
- APKs are built by GitHub Actions, never locally. After every push, watch the run until it finishes, read the check
  annotations, and **fix build errors and warnings automatically** (including deprecated actions) before reporting.
- Report results briefly: what changed, the build name (e.g. `GBSat-v1.3.1-build141`), and what still needs a device test.

## Releases

- **Only release when the owner asks** and gives the version. A release is published automatically by CI when
  `apps/android/VERSION` changes, so never bump `VERSION` otherwise.
- Release notes come from the matching `## x.y.z` section of `CHANGELOG.md`; keep an `## Unreleased` section on top and
  add a line for every user-visible change.
- The release has the **APK only**; the App Bundle (`.aab`, for Google Play) stays in the build artifacts.
- `versionCode` = CI run number (always increasing); `versionName` = `VERSION (build N)`.
- Never create tags by hand. Commit/push only to the app's working branch (GBSat: `android-port`); no pushes upstream.
- This session type cannot edit or delete releases: tell the owner how to do it in the GitHub UI.

## License

- This kit is GPLv3, so every app built on it is distributed under GPLv3 with public source. Check the core's license
  before starting: it must be GPLv3-compatible (GPLv2 "or later", GPLv3, MIT, BSD, Apache 2.0); "GPLv2 only" cores can't
  use the kit.

## Signing

- All APKs/AABs of an app are signed with one permanent release key from repository secrets (GBSat:
  `GBSAT_KEYSTORE_BASE64` and `GBSAT_KEYSTORE_PASSWORD`, alias `gbsat`), so updates install over each other.
- A new app gets its own key (generate it, send it to the owner, ask them to add the secrets). On Google Play, upload
  with "use my own key" so the Play and GitHub builds share the signature.

## Google Play requirements (keep them from day one)

- targetSdk/compileSdk **36**, Android Gradle Plugin 8.10+, Gradle 8.14.
- **16 KB page size**: `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` and `-Wl,-z,max-page-size=16384`; CI checks alignment.
- **No `MANAGE_EXTERNAL_STORAGE`**: games are read through the Storage Access Framework (folder picker + persisted
  tree URI). The kit's `SafGames` stages disc files as `/proc/self/fd/N` symlinks so path-based cores work unchanged.
- Data stays in the app's storage, with Export (saves and save states only) / Import data.
- `enableEdgeToEdge()`, no deprecated status/navigation bar colors; screens use `safeDrawingPadding`.
- With targetSdk 36 (predictive back), map the controller's B straight to `onBackPressedDispatcher.onBackPressed()`
  instead of synthesizing `KEYCODE_BACK`.
- Privacy policy file (`PRIVACY.md`) in the repo; no ads, no analytics.
- No BIOS or games included; no console maker trademarks in the app name, icon or subtitle.

## Interface and controller behavior

- Use this kit (`gb.frame`): `EmuTheme` with the app's palette (GBSat: `EmuPalettes.Violet`; new apps:
  `EmuPalette.fromAccent(color)`), settings blocks, `ConfirmDialog`, `MenuIconView`, `PerfOverlay`, `rememberHasGamepad`, the first-run `SetupWizard`
  (no preset question: the preset is chosen from `DeviceTier`, and Settings → Performance shows the device class and it), box art (`Covers`, libretro-thumbnails), the home screen (`HomeTopBar`, `HomeLibrary`, `HomeTheme`) and RetroAchievements
  (`RetroAchievements` + `RaBackend`, `RetroAchievementsSettings`, `RaUserHeader`, `AchievementsScreen`, offline
  casual unlocks through `RaOffline`), the games folder (`SafGames`), hotkeys (`Hotkeys`), per-game recommended
  settings (`GameProfiles` by `DeviceTier`), play time
  (`PlayTime`), game-named save folders with Export/Import (`SaveData`), the "Resume" view of save states with
  screenshots (`ResumeGrid`, only the latest save state of each game) and button mapping on a drawing of the console's controller (`PadDiagram` + `PadLayout`).
- Save states: **Auto save** writes the auto slot when the game is exited (in-game Exit, quitting the app) **and when
  the app goes to the background** during a game (Home, recent apps: Android may close it there). **Auto load**
  continues from the game's **latest save state in any slot** (auto, quick or numbered), so it also works with Auto
  save off; the Resume view and the "Open" game option (without auto load) still start where the user chooses.
- Settings the user changes through the interface are remembered between sessions, including UI state such as the
  library layout (all games / recent save states: `LibraryView`), so the app reopens the way it was left.
- Controller: **A** confirms, **B** goes back (Exit dialog in the library), **X** opens the settings menu, **Y** opens the
  game options, **Select** switches all games / recent save states (handled in the Activity so it works without focus),
  **Start** does nothing in the library. The top bar is touch-only.
- Every dialog and menu must work with the controller from the first press: draw dialogs in the app window, give the
  first item focus, never let the game `SurfaceView` take key focus, and let Android turn hat-axis D-pads into D-pad keys
  while a menu is open.
- Home screen: `HomeTopBar` (app icon, short label, clock, battery icon; a tap opens the menu) and `HomeLibrary` in the
  theme picked in Extras (`HomeThemeSettings`): **Showcase** (default: selected game large on top over its blurred cover,
  shelf below), **Grid** (settings as an icon rail, box art grid), **List** (titles + preview with the latest save state)
  and **Continue** (latest save states first, then all games). The settings menu drops down below the top bar (Grid:
  the rail).
- The button help bar is shown only when a physical controller is connected.
- The game picture is aligned to the top of the screen: when the screen is narrower than the picture (square, 5:4, a
  16:9 mode on 4:3) all the black bars go below it; side bars stay centered.
- Touch controls are hidden automatically while a physical controller is connected (handhelds' built-in controls count).

## Performance lessons (from GBSat on the T618)

- Pin the emulation thread alone on the fastest core (rank cores by `cpu_capacity`) and move threads inherited from
  it off that core. Threaded sound depends on the game and the device (on the T618 VF2/Panzer want the second
  fast core for VDP2, Burning Rangers/NiGHTS for the sound): offer Auto (try both when slow, keep and remember the
  faster one per game) plus On/Off. Measure thread waits (performance log) before changing thread layouts.
- Keep the Android performance hint session on the emulation thread only (never recreate it).
- Present frames zero-copy through AHardwareBuffer; automatic frame skip only when behind.
- Changing threading options mid-game must not leave lasting slowdowns.

## Screenshots

- README/store screenshots are rendered from code with Robolectric/Roborazzi (sample covers, no commercial art), by a
  workflow that commits them to `docs/screenshots`.
