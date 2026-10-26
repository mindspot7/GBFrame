# GBFrame app integration standard

This document is the normative checklist for apps that consume GBFrame. GBFrame owns reusable UI and behavior; each app owns its identity, core adapters, and CI outputs.

## How to identify a rule

Use sources in this order:

1. This document and the GBFrame README are the cross-app contract.
2. Public GBFrame APIs and their KDoc define required inputs and behavior.
3. A focused standard, such as [QUICK_MENU_STANDARD.md](QUICK_MENU_STANDARD.md), defines the details for that feature.
4. GBSat is the executable reference for integrations that the docs do not yet specify.

Do not treat an app-specific implementation as a standard when it conflicts with GBFrame documentation. Resolve a discrepancy by updating the contract and aligning the affected consumers.

## App identity

- Use the same lowercase stable slug for Android `applicationId` and Gradle `namespace`: `app.<slug>.emulator`.
- Use GBFrame's `emulatorLabel(manufacturer, console)` and shared `AboutPage`; do not fork the About layout. The package identity line must read `appid: <applicationId>` (not `Application id`).
- Provide app-specific credits and license text to `AboutPage`.

## App icon and in-app identity

The consuming app owns its launcher icon in its own Android resources; GBFrame must not embed a particular app's logo.

- Use the app's launcher icon artwork in `HomeTopBar`, `HomeMenuPanel`, and `AboutPage`. These three places must show the same icon identity as the installed app.
- Pass an inline-compatible raster `Painter` to `HomeTopBar`, `HomeMenuPanel`, and `AboutPage`. Prefer the app-owned raster resource (for example, `painterResource(R.drawable.ic_launcher)`) for all three.
- **Do not pass an adaptive launcher XML resource** (typically `mipmap-anydpi-v26/ic_launcher.xml`) to Compose `painterResource()`: it may fail at runtime because it is not a supported Compose painter resource. Android may still use that adaptive resource for the launcher; the in-app UI must use a raster-compatible resource.
- If the app's launcher resource is adaptive or otherwise unsupported inline, provide a raster resource derived from the exact same icon artwork. Do not substitute a different logo or core icon.
- Keep the icon artwork and displayed app name consistent across the top bar, main menu, and About page. Do not add a GBFrame-specific outline.
- Each app owns and supplies its own icon resources; GBFrame accepts a `Painter` and must not resolve another app's resource IDs itself.
- Do not omit the app icon from the top bar or About page.

## CI artifact names

GBFrame does not build or name consumer APKs; each app's workflow must follow this filename convention:

- Development APK and uploaded Actions artifact: `<AppName>-v<versionName>-build<runNumber>`. The APK filename is `<AppName>-v<versionName>-build<runNumber>.apk`, and the uploaded artifact uses the same stem.
- Numbered release APK: `<AppName>-v<releaseVersion>.apk`. The release version is the app's public version; development build numbers do not replace it.
- Use the app's packaged Android `versionName` in the filename. Keep `versionCode` unique and monotonically increasing for updates.
- Do not expose a core or template filename such as `app-release.apk` or `flycast-release.apk` as the user-facing result.

For example, a development build can be `GBSat-v1.3.6-build402.apk`; its Actions artifact is named `GBSat-v1.3.6-build402`.

## Library input and navigation

The library must use GBFrame's shared input and Back helpers rather than an app-local menu state machine.

- Route library shortcut events through `HomeKeys.dispatch`. Disable those shortcuts while controller remapping is capturing input, then give the event to `ControlsCapture.dispatch` so the requested button can be recorded.
- Handle library Back through `LibraryBack`, preserving the shared menu/screen stack and the platform Back behavior. Do not independently toggle the menu from a second state variable.
- Give GBFrame shortcut and capture helpers the event before app-specific key remapping or core forwarding. Preserve the original device, action, and timing when forwarding or translating an unconsumed event.
- Some controllers report the same physical Back/B press as both a gamepad button and Android Back. Collapse only the paired duplicate from the same device/press; do not consume an unrelated system Back event or dispatch one press twice.
- In the library, verify the configured Home menu shortcut opens the shared main menu and B/Back closes one level at a time. During remapping, verify those same buttons can be captured without firing the shortcut.
- During gameplay, follow [QUICK_MENU_STANDARD.md](QUICK_MENU_STANDARD.md): Android Back opens the quick menu, while ordinary gameplay B remains a game input.

## Game selection, save slots, and launch

Use GBFrame's `HomeLibrary` and `HomeActions` for the library selection UI. Preserve the callback meanings in the public API:

- `play(game)` is A/tap and starts the game using the app's normal auto-load policy.
- `fresh(game)` starts the game without loading a saved state.
- `options(game)` opens the per-game options from Y/long-press.
- `resume(entry)` loads the exact selected `ResumeEntry` state.
- Save the selected game before launching so the library can restore its selection when the user returns. For asynchronous core startup, prevent duplicate launch requests, show GBFrame's `LaunchLoadingOverlay` until the handoff finishes, and provide failure feedback so the user is not left with a blocked library.

Use `SafGames.scan` for the shared SAF library. For a synchronous core load callback, use `SafGames.openAndLoad`; it retains opened disc resources for the session and applies the shared CHD/ISO copy fallback. Close `OpenDisc` on load failure or when the game session ends. An asynchronous/path-based core may use `SafGames.open` and its own adapter, but must preserve the same open lifetime and error handling. If the core reopens referenced tracks by path, materialize the staged descriptors into usable files before releasing them; keep this core-specific staging in the app adapter.

The shared manual save-state limit is `SaveData.MANUAL_SAVE_STATE_SLOTS = 5`. Use that constant instead of app-local counts and expose at most five numbered slots per core. Quick-save, auto-save, and other automatic states are separate features and do not increase the numbered-slot limit.

## Google Play readiness

- Use the Storage Access Framework and app-private storage. Do not request broad read/write external-storage permissions or legacy external-storage behavior.
- Keep automatic cover downloads and every fallback provider optional and replaceable. Before enabling a provider in a Play build, document its API authorization, attribution and media-licensing terms; an open-source API client does not by itself license the returned cover artwork.
- Do not bundle BIOS files, games, console logos or third-party cover art. Store-listing screenshots and promotional art must use material the publisher is authorized to distribute.
- Keep a repository privacy policy synchronized with every network endpoint, credential/token, crash-report option and Android permission used by the app.

## Gameplay pause and Android foreground lifecycle

Every consumer must preserve the distinction between **a pause requested by the player** (for example, the in-game menu) and **a pause imposed by Android lifecycle** (the activity leaving the foreground). Returning to the app must not leave gameplay accidentally paused, and must not dismiss or override an intentional pause.

- In `Activity.onPause()`, capture whether a game is running and gameplay was active **before** the lifecycle handler pauses it. Store this as a one-shot resume intent; do not infer the previous state later from a paused flag that the lifecycle callback itself has already changed.
- Pause the running core when the activity leaves the foreground, using the core adapter's explicit pause API where available.
- In `Activity.onResume()` (or the equivalent foreground callback for the core's rendering activity), consume and clear the one-shot resume intent. Resume only if the intent was recorded and the game session is still running.
- If gameplay was already paused when Android backgrounded the app, preserve that pause. In particular, do not automatically close the quick menu or resume a core that the player intentionally paused.
- Prefer idempotent explicit state setters such as `pause(false)` over native toggle operations. If a core exposes only a toggle, inspect its current state and invoke the toggle only when the state is known to be opposite the desired state; never use an unguarded toggle as a lifecycle recovery action.
- Keep the core-specific lifecycle adapter in the app, but document this behavior here and apply it consistently to every GBFrame consumer. GBFrame's shared UI cannot infer or control the native core's lifecycle by itself.
- Validate on a real Android device: (1) background and return during active gameplay; (2) background and return while the in-game menu is open; (3) background and return after explicitly pausing by another supported route; (4) repeat the cycle and verify no duplicate pause/resume or stuck state. Confirm the game resumes in case (1), while intentional pauses remain paused in cases (2) and (3).

The GBFrame integration contract is considered device-validated for GBSat and GBCast after the October 2026 lifecycle fix and successful user testing in both apps. New consumers must still run the same checks on their own core/activity integration.

## Box-art dimensions

Follow [BOXART_STANDARD.md](BOXART_STANDARD.md) when initializing GBFrame's shared covers:

- Determine the dominant width-to-height ratio of the system's named box-art images, using a full catalog when practical or a representative sample stratified across regions and titles.
- Pass that ratio as `frameAspectRatio` to `Covers.init`. GBFrame normalizes downloaded and user-selected art to this shared frame.
- Let GBFrame's `HomeLibrary` and `CoverCard` use the same ratio in Showcase, Grid, and All in One. Do not add app-local dimensions or stretch a second time.
- Keep the existing Saturn frame ratio at `0.66f`; systems whose box-art majority differs must use their measured ratio.

## Shared versus app-owned code

Reuse GBFrame components and helpers for shared screens, menu state, navigation, controller capture, library shortcuts, and Back behavior. The app supplies real core actions, persistence, metadata, its launcher icon resources, and adapters such as `RaBackend` or `CheatsBackend`. When a shared helper documents dispatch order or lifecycle, follow that contract rather than adding a parallel app-local state machine.

- **Touch controls:** hide/disable on-screen touch controls whenever a non-virtual physical gamepad or joystick is connected, including built-in handheld controls. Detect devices by `SOURCE_GAMEPAD` or `SOURCE_JOYSTICK` and ignore virtual devices. Re-evaluate on device add/change/remove so touch controls reappear when the last physical controller disconnects. The user's touch-controls preference is still respected when no physical controller is connected.


## Color and theme contract

Every consumer must use GBFrame's `EmuTheme` as the single root theme provider. Do not create a parallel app-local Material color scheme or fork shared component styling. App-specific screens must read `palette` / `MaterialTheme.colorScheme` instead of hard-coding decorative colors.

- **Consistency:** all shared and app-owned screens (library, settings, quick menu, dialogs, wizard, About and gameplay overlays) must use the active `EmuPalette` and Material color scheme. Palette changes must propagate through the entire UI, not only the library.
- **No arbitrary decorative colors:** shared components must use palette tokens for accents, highlights, surfaces, panels, cards, borders and secondary text. Fixed colors are reserved for semantic statuses (for example, warning/error) or for artwork/content that is not UI chrome; semantic colors must remain consistent and legible across palettes.
- **Contrast and disabled states:** text and icons must remain distinguishable from their surfaces. Use `MaterialTheme.colorScheme` and component color APIs, including explicit disabled-content/container colors when a component does not provide a suitable accessible default. Do not simulate disabled controls only by lowering opacity to an unreadable level. Keep warning/error colors distinguishable from normal accents.
- **One root provider:** select the app palette exactly once at the root with `EmuTheme(palette = ...)`. Do not nest a second `MaterialTheme`, maintain a duplicate palette, or set local hard-coded backgrounds that visually override the active theme. New shared components must obtain the current palette through `palette` and use Material colors for standard controls.
- **Review rule:** changes to shared UI must be checked against at least the default palette and a contrasting alternative palette, including enabled, selected, warning and disabled states.
