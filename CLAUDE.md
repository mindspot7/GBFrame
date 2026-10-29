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
- Report results briefly: what changed and the build name (e.g. `GBSat-v1.3.1-build141`). Mention CI only when something
  went wrong, and don't say that a build wasn't tested on a device (the owner knows); say what to test only when it's
  not obvious.
- After a push, send the written analysis/implementation reply **right away, while CI runs**; watch the run in the
  background and then report only the build name (and any CI problem). Don't send messages about background
  notifications that change nothing (expired monitors and the like).
- Profiling builds only when needed: the test script's profile option generates one when the latest is older than the
  normal build (same version code). Don't start one after every push.

## Device tests (test script)

- Scripted device tests (Claude Code on the owner's PC) use the app's copy of `tools/emu-test.ps1` (GBSat: `tools/gbsat.ps1`):
  non-interactive commands that write to a git-ignored `test-output/` folder. `tools/check-app-id.sh` (run by a small CI workflow)
  keeps the applicationId, namespace, CMake `APP_ID`, manifest activity and the device tools in agreement; an app's `CLAUDE.md`
  lists the commands. The owner tests with the app's copy of `tools/emu-test.bat` (GBSat: `tools/gbsat.bat`) in the adb platform-tools
  folder on his Windows PC, connected to the session: 1 install the latest build (deletes the APK after installing),
  2 performance log, 3 CPU profile, 4 library scroll measurement, 5 PGO data, 6 A/B diagnostic switches, 7 wake the
  device, 8 screen capture. Files are named after the game (`<app>-perf-log_<GAME>.csv`, `<app>-profile_<GAME>.txt`,
  `<app>-tela_<GAME>.png`; the game comes from the log's `game` column).
- When the owner says **"pronto"**, read the new files in that folder and analyze them (compare with the previous run
  of the same game and scene). Keep the script in the app repo and write every change to the owner's copy too, then
  check the file on his PC (size/content): a write once didn't land.
- Suspect a regression? Add an A/B switch (`gb::DiagOn`, `debug.<app>.<name>`, shown as `diag=` in the log) and let
  the owner flip it in one run with option 6 instead of comparing builds; he reports what he sees.
- For visual changes (shaders, anti-aliasing, colour), first show previews computed from his own captures (option 8)
  at his screen's resolution, then build.
- Look at the reference emulators' sources first (DuckStation/SwanStation for features, RetroArch slang-shaders for
  shaders) and adapt; when the reference is closed (e.g. DukeStation's Skip Engine), implement the idea and say so.
- "Gere versão x.y.z": set `VERSION` to x.y.z, move `## Unreleased` into a `## x.y.z` section (with a new empty
  `## Unreleased` on top), push, and report the build.

## Releases

- **Only release when the owner asks** and gives the version. A release is published automatically by CI when
  `apps/android/VERSION` changes, so never bump `VERSION` otherwise.
- Release notes come from the matching `## x.y.z` section of `CHANGELOG.md`; keep an `## Unreleased` section on top and
  add a line for every user-visible change.
- The release has the **APK only**; the App Bundle (`.aab`, for Google Play) is built only for a release (VERSION
  without a GitHub release yet) or a manual run with `bundle: true`, and stays in that run's artifacts.
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
- No BIOS or games included; no console maker trademarks in the app name or icon. The About label (`emulatorLabel`: `<Manufacturer> <Console> Emulator for Android`, e.g. "Sega Saturn Emulator for Android") only names the console the app emulates, and the license text keeps the "not affiliated with <Manufacturer>" notice.

## Interface and controller behavior

- Use this kit (`gb.frame`): `EmuTheme` with the app's palette (GBSat: `EmuPalettes.Violet`; new apps:
  `EmuPalette.fromAccent(color)`), settings blocks, the standard `AboutPage` (label, version, Application id, credits, license), the standard `ControlsPage(host)` (Controller / Hotkeys / Touch tabs, the console's controller drawing, button mapping, hotkeys), `ConfirmDialog`, `MenuIconView`, `PerfOverlay`, `rememberHasGamepad`, the first-run `SetupWizard`
  (no preset question: the preset is chosen from `DeviceTier`, and Settings → Performance shows the device class and it), box art (`Covers`, libretro-thumbnails), the home screen (`HomeTopBar`, `HomeLibrary`, `HomeTheme`, `HomeMenuPanel`) and RetroAchievements
  (`RetroAchievements` + `RaBackend`, `RetroAchievementsSettings`, `RaUserHeader`, `AchievementsScreen`, offline
  casual unlocks through `RaOffline`), the games folder (`SafGames`), starting a game from an intent for frontends
  (`LaunchIntent`: the library Activity calls `handle` in `onCreate`/`onNewIntent`, the root composable resolves
  `pending` against the library by file name and starts it like a cover tap), hotkeys (`Hotkeys`), per-game recommended
  settings (`GameProfiles` by `DeviceTier`), play time
  (`PlayTime`), game-named save folders with Export/Import (`SaveData`), the "Resume" view of save states with
  save-state screenshots within All in One and button mapping on a drawing of the console's controller (`PadDiagram` + `PadLayout`).
- Save states: **Auto save** writes the auto slot when the game is exited (in-game Exit, quitting the app) **and when
  the app goes to the background** during a game (Home, recent apps: Android may close it there). **Auto load**
  continues from the game's **latest save state in any slot** (auto, quick or numbered), so it also works with Auto
  save off; the Resume view and the "Open" game option (without auto load) still start where the user chooses.
- **Fast boot** is mandatory in every emulator and **on by default** (System → Loading Times, `SystemStartupOptions.fastBoot`
  is required): mark the firmware's first-run setup (clock, language) as done so the BIOS skips those screens, and run
  the BIOS intro animation at maximum speed (muted) until the disc's code takes over. See
  [docs/SYSTEM_MENU_STANDARD.md](docs/SYSTEM_MENU_STANDARD.md).
- Every performance option goes both in Settings → Performance and in the in-game quick menu → Performance (so it can
  be compared while playing), and into the performance log's `settings` column.
- Settings the user changes through the interface are remembered between sessions, including UI state such as the
  selected game (`LibraryView`) and the game selected last (the library
  opens on it again after a game, a menu or a settings page, and at the next start), so the app reopens the way it was left.
- Controller: **A** confirms, **B** goes back (Exit dialog in the library; inside a settings page it returns to the menu,
  on the entry that was opened), **X** opens/closes the main menu, **Start** cycles the library theme
  Showcase/Grid/All in One (`HomeTheme.cycleStyle()`, same as the menu's **Theme** tile), **Y** opens the game options,
  **Select** toggles library fullscreen (handled in the Activity so it works without focus), and the menu's **Fullscreen**
  tile toggles the library display (also available via Select). There is no separate Recent mode. The top bar is touch-only.
  The help bar lists the buttons from the right as A, Y, X, B. This is the kit's behavior, the same in every app:
  `MenuState` (menu/quit/focus state), `HomeKeys.dispatch(event, enabled)` (X, Start, Select, A on the quit dialog, lost
  focus; call it first in the Activity's `dispatchKeyEvent`, with `enabled = false` while a game runs or a button is being
  captured), `LibraryBack.handle { … }` (from the app's `BackHandler`), the Exit dialog (`HomeQuitDialog(onQuit)` drawn last
  over the library, `Modifier.homeQuitKeys(onQuit)` on the library's root) and `HomeHelpBar()`. Apps do not reimplement them.
- Every dialog and menu must work with the controller from the first press: draw dialogs in the app window, give the
  first item focus, never let the game `SurfaceView` take key focus, and let Android turn hat-axis D-pads into D-pad keys
  while a menu is open.
- Home screen: `HomeTopBar` (app icon, app name with a menu arrow, clock, battery icon; a tap opens the menu) and
  `HomeLibrary` has three layouts: **Showcase** (default: selected game large on top, one-line title that scrolls when long, shelf below), **Grid** (grid of 3D boxes drawn from the 2D covers: `BoxArt3D`) and **All in One** (latest save states first, then all games). Theme cycling and library fullscreen are exclusively in the `HomeMenuPanel` LIBRARY tiles; they are not settings pages. The main menu slides in from the left (X, or a tap on the top bar), with settings as icon tiles and Quit; it stays open while a settings page is shown, so Back returns to it on the same entry.
- Use `setControllerContent` instead of `setContent`: after a touch, Android spends the first D-pad press leaving touch
  mode, so moving needed two presses; it sends D-pad keys in touch mode straight to the Activity.
- X (menu), Start (theme) and getting the focus back after a tap are handled in the Activity, since Compose key handlers only see keys
  while something is focused; `HomeLibrary(refocus = …)` puts the focus back on the library.
- Controls page (Settings → Controls): an app implements `ControlsHost` (its console name, `PadLayout`, `ControlButton` list,
  where bindings/hotkeys/options are stored; see GBSat's `SaturnControls`) and shows `ControlsPage(host) { console-specific rows }`
  plus `ControlsCapturePrompts(host)` over the settings screen. `ControlsCapture.dispatch(event, host)` (first in the
  Activity's `dispatchKeyEvent`, and `ControlsCapture.active(host)` as `HomeKeys.dispatch`'s `enabled` condition) turns the
  next presses into bindings. Tabs, controller drawing, mapping rows, hotkey rows, prompts and texts are the kit's.
- While the main menu is open the app passes `HomeLibrary(focusEnabled = false)`, so the library never competes with the
  menu for the focus (that race made the menu lose its selected entry when returning from a settings page); when the menu
  closes (or the layout changes) the library takes the focus back on the game selected last. The focus requester sits on the
  game selected *now* (`LibraryView.lastGame`), not on the one the screen opened on.
- The button help bar is shown only when a physical controller is connected and `HomeTheme.buttonHelp` (Extras → Layout → Button help) is on.
- Programmatic focus (first game, first menu entry, refocus after a tap) only happens with a physical controller (`rememberHasGamepad`); a menu opened by touch also selects its first entry then, after `requestFocusFromTouch()` leaves touch mode.
- The legacy `HomeThemeSettings(onLayoutChanged)` entry point is intentionally empty for compatibility; layout and fullscreen are controlled from the main menu, not Settings.
- The game picture is aligned to the top of the screen: when the screen is narrower than the picture (square, 5:4, a
  16:9 mode on 4:3) all the black bars go below it; side bars stay centered.
- Touch controls are hidden automatically while a physical controller is connected (handhelds' built-in controls count).

## Performance lessons (from GBSat on the T618)

- Pin the emulation thread alone on the fastest core (rank cores by `cpu_capacity`) and move threads inherited from
  it off that core. Threaded sound depends on the game and the device: offer Auto (try both when slow, keep and remember the
  faster one per game) plus On/Off. The October 2026 performance log on the T618 (threaded VDP on) showed threaded
  sound faster in every game tested, VF2 included (58.8 vs 53-54 fps); Auto starts with threaded sound on (every device); keep threaded VDP always on there (off costs
  2-5 ms per frame). The SH-2 interpreter is ~65-85% of the emulation thread's frame time. Measure thread waits (performance log) before changing thread layouts.
- The performance log is the main device-test tool: per-component frame time, CPU per thread (`perf_probe`, sampled
  on a background thread so /proc reads never stall the emulation), core clock, thermal status, device and the options
  in use. Ask the owner for it before guessing at a slowdown.
- Keep the Android performance hint session on the emulation thread only (never recreate it).
- Present frames zero-copy through AHardwareBuffer; automatic frame skip only when behind, spaced (frames shown
  between skips adapt every second) so the picture doesn't drop to every other frame near full speed.
- Audio-driven pacing needs dynamic rate control (play up to 5% slower while the buffer is low): at 59.8 fps the
  buffer otherwise hovers near empty and every callback that runs short clicks.
- Changing threading options mid-game must not leave lasting slowdowns.

## Screenshots

- README/store screenshots are rendered from code with Robolectric/Roborazzi (sample covers, no commercial art), by a
  workflow that commits them to `docs/screenshots`. It runs when `VERSION` changes (a release) or by hand
  (`workflow_dispatch`), not on every push.
