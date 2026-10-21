# Project conventions (GB emulator family)

These rules apply to every app built on this UI kit (GBSat and the next emulators). Read them before working on any
of these repositories.

## Documentation

- Read [docs/LESSONS.md](docs/LESSONS.md) before starting an app, and add a line whenever an app teaches something new.
- Every app has its own README.md written from [docs/APP_README_TEMPLATE.md](docs/APP_README_TEMPLATE.md); keep it
  up to date with user-visible changes.

## Working with the owner

- Talk to the owner in **Portuguese (pt-BR)**; code, comments, commit messages and CHANGELOG stay in English.
- The owner tests on real devices: an Adreno 740 handheld (Snapdragon 8 Gen 2) and a Unisoc T618 / Mali-G52 device with
  a 720x720 square screen. Keep both in mind (performance, small screens).
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
  tree URI). GBSat's `Storage.kt` stages disc files as `/proc/self/fd/N` symlinks so path-based cores work unchanged.
- Data stays in the app's storage, with Export (saves and save states only) / Import data.
- `enableEdgeToEdge()`, no deprecated status/navigation bar colors; screens use `safeDrawingPadding`.
- With targetSdk 36 (predictive back), map the controller's B straight to `onBackPressedDispatcher.onBackPressed()`
  instead of synthesizing `KEYCODE_BACK`.
- Privacy policy file (`PRIVACY.md`) in the repo; no ads, no analytics.
- No BIOS or games included; no console maker trademarks in the app name, icon or subtitle.

## Interface and controller behavior

- Use this kit (`gb.ui`): `EmuTheme` with the app's palette (GBSat: `EmuPalettes.Violet`; new apps:
  `EmuPalette.fromAccent(color)`), settings blocks, `ConfirmDialog`, `MenuIconView`, `PerfOverlay`, `rememberHasGamepad`, the first-run `SetupWizard`
  (always ending with a `PresetPicker` step), box art (`Covers`, libretro-thumbnails) and RetroAchievements
  (`RetroAchievements` + `RaBackend`, `RetroAchievementsSettings`, `RaUserHeader`, `AchievementsScreen`), play time
  (`PlayTime`), game-named save folders with Export/Import (`SaveData`), the "Resume" view of save states with
  screenshots (`ResumeGrid`, only the latest save state of each game) and button mapping on a drawing of the console's controller (`PadDiagram` + `PadLayout`).
- Controller: **A** confirms, **B** goes back (Exit dialog in the library), **X** opens the settings menu, **Y** opens the
  game options, **Select** switches all games / recent save states (handled in the Activity so it works without focus),
  **Start** does nothing in the library. The top bar is touch-only.
- Every dialog and menu must work with the controller from the first press: draw dialogs in the app window, give the
  first item focus, never let the game `SurfaceView` take key focus, and let Android turn hat-axis D-pads into D-pad keys
  while a menu is open.
- Landscape: settings menu docked on the left; portrait/square: drop-down menu over the screen.
- The button help bar is shown only when a physical controller is connected.

## Performance lessons (from GBSat on the T618)

- Run sound (SCSP-like chips) on its own thread; pin the emulation thread alone on the fastest core (rank cores by
  `cpu_capacity`), and move threads inherited from it off that core.
- Keep the Android performance hint session on the emulation thread only (never recreate it).
- Present frames zero-copy through AHardwareBuffer; automatic frame skip only when behind.
- Changing threading options mid-game must not leave lasting slowdowns.

## Screenshots

- README/store screenshots are rendered from code with Robolectric/Roborazzi (sample covers, no commercial art), by a
  workflow that commits them to `docs/screenshots`.
