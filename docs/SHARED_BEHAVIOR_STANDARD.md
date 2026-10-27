# GB-family shared behavior standard

This is a normative contract for every emulator consuming GBFrame. It consolidates the cross-app requirements established during the October 2026 GBCast/GBSat updates. Follow it together with [APP_INTEGRATION_STANDARD.md](APP_INTEGRATION_STANDARD.md), [QUICK_MENU_STANDARD.md](QUICK_MENU_STANDARD.md) and [BOXART_STANDARD.md](BOXART_STANDARD.md).

An app-specific implementation is evidence for a requirement, not a reason to copy that console's settings into another emulator. Document unsupported capabilities explicitly; do not present inactive controls as working features. Updating a submodule or this document does not prove that a consumer's core adapter conforms.

## Ownership and capabilities

- GBFrame owns reusable presentation, navigation, focus, theme, generic persistence helpers and the integration contracts.
- Each consumer supplies its identity, controller artwork/geometry, cover profile, firmware requirements, supported display modes, cheat catalog/parser, native calls and core lifecycle adapter.
- Keep console names, BIOS filenames/sizes, RAM addresses, serial IDs and core-specific slot numbers out of shared defaults. The shared manual-slot count is a UI contract, not a requirement that cores use identical storage indices.
- Expose only display modes, renderers, metrics and core operations that the consumer can implement. Avoid duplicate app-local versions of shared screens.
- User-facing identity uses the consumer's app name. Preserve upstream attribution, licenses and technically necessary native symbols/file formats; do not rename them mechanically just to remove an upstream name.
- Choose Android ABIs from the devices actually supported by the app. Android alone does not imply an ARM-only architecture; do not turn a consumer's release-ABI choice into a framework-wide requirement.

## Identity, selected text and controller diagrams

- Use the same app-owned controller artwork for the launcher, library header, main menu and About. Preserve aspect ratio, transparent foreground, continuous black contour and black flattening/fallback background, following the icon composition contract in the app integration standard.
- Use `rememberAppIconPainter` for in-app identity so launcher safe-zone padding does not shrink the visible artwork. Retain the standard frame dimensions; do not compensate by stretching a controller or changing each menu independently.
- Selected segmented options must keep readable theme text colors. Reuse `ChoiceRow` and the active theme rather than introducing black selected text on dark surfaces.
- Use `ControlsPage` and `PadLayout`. The consumer supplies the actual system's pad shape/artwork, aspect ratio and measured button positions. Button targets, labels and remapping actions must agree with that drawing at handheld and phone sizes.

## Library startup, covers and disc access

- On app startup, select and focus the last opened game in every library layout. Record successful launch handoffs with `LibraryView.gameOpened`; merely browsing other covers must not replace the startup target. Keep session selection when returning from a menu. Fall back to an available game when the remembered one is missing.
- Preserve `HomeActions` semantics: normal Play follows auto load, Start fresh bypasses it, and Resume loads the exact selected state. Block duplicate launch requests and recover visibly from failures.
- Open games directly through SAF/session-owned descriptors first. Use the shared supported-format copy fallback only when direct access fails on that device/core. Keep all required disc/track resources alive through the session and close them on failure or exit.
- Display `LaunchLoadingOverlay` while opening or preparing a fallback copy. Do not leave the user on an unresponsive library during copying.
- Use the shared Cover Image chooser with Web Search and Local Search. Web Search uses the same title/system query, allows image selection and preview, applies only after confirmation, invalidates the cover cache/version and returns to the library.
- Back from web search traverses browser history, chooser, game options and library, one level per press. Provide an explicit Close search action as well.
- Bound and validate downloaded/local images before atomically replacing an existing custom cover. The cover flow must allow an optional secondary provider after the primary finds no match; providers must be replaceable and meet the authorization/attribution/media-license requirements in the app integration standard before being enabled. Never replace an existing custom cover with a provider fallback. Each consumer retains its own measured cover ratio; no other console's format becomes a shared default.

## Main settings and quick-menu placement

- Keep the common main settings sections: System, Video, Audio, Performance, Controls, Extras and About, omitting only genuinely unsupported sections. Use Extras for optional networking/achievement features rather than a core-branded Network section.
- System contains the shared `SaveStateSettings` Auto save and Auto load switches, backed by persistent global preferences. Do not duplicate these switches in quick-menu Game.
- Video contains one mutually exclusive Display mode selector. Offer original aspect, supported widescreen modes and Fullscreen according to capabilities. Keep 16:9 distinct from an expanded view matching the device ratio and from stretching an original picture to fill the window. Changing mode clears incompatible flags and obsolete stretching state.
- Video in the main settings contains an Overlays group. Quick-menu Overlays controls the same persisted monitoring preferences. Do not duplicate Show FPS or other monitoring controls under quick-menu Video.
- Do not expose Rotate in quick-menu Video. Orientation follows the device; any real core-specific rotation setting belongs in the appropriate main settings capability.
- Use the standard quick-menu hierarchy, row order, save-state layout and Back stack. Optional per-game overrides and native advanced settings remain consumer capabilities and cannot replace the shared UI.

## Save states, auto load and lifecycle

- Expose five numbered manual slots through `SaveData.MANUAL_SAVE_STATE_SLOTS`, or fewer when the core supports fewer. Automatic/quick states use separate storage and must never overwrite a numbered manual slot as an accidental fallback.
- Display `QuickMenuStateSlot` with screenshot, slot label, timestamp or Empty. Disable loading empty slots. For older states without images, retain the shared placeholder; do not claim an old missing screenshot was recovered.
- Capture the actual game frame on the thread/context required by the renderer. Store the screenshot with the completed save and refresh the list after success. Keep expensive work off the UI thread where possible; report failures and prevent duplicate save requests.
- Auto save runs on a clean game exit and when leaving the foreground, if enabled and allowed. Complete or coordinate pending writes before unloading the session; do not replace a valid preview with a later capture from a destroyed renderer.
- Normal Play with Auto load enabled uses the game's latest supported state, including manual, quick or automatic states. Start fresh bypasses loading. Explicit Resume uses its requested slot regardless of the global Auto load preference.
- Select/load the requested state after core initialization and per-game settings reload. Do not pass a special automatic slot through a manual-slot clamp that silently redirects it to an empty or different slot. Keep the manual slot selection intact.
- Preserve temporary Auto load overrides and restore the user's preference on success and failure. Observe the lifecycle contract: foreground recovery resumes only gameplay that Android interrupted, while intentional pauses and open menus remain paused.
- Apply achievement-mode restrictions through the real backend. A forbidden operation must not report success.

## Gameplay placement, overlays and mixed input

- Keep the configured game aspect horizontally centered and top-aligned whenever vertical space remains, including square screens and portrait phones. Unused space stays below the picture. Fullscreen stretch remains an explicit display-mode choice.
- Menus/overlays cover the full app window; pointer/lightgun coordinates must use the actual video rectangle. Respect device orientation and restore any temporary display overrides used during tests.
- Use GBFrame `PerfOverlay` and shared achievement notifications. Replace duplicate core-native FPS, pause and generic notification overlays. Preserve hardware-specific displays or controls only when they implement a distinct supported capability without duplicating shared UI.
- Offer the same supported monitoring metrics through main Video/Overlays and quick-menu Overlays: FPS, speed, renderer, resolution, CPU, GPU and frame time, in the quick-menu standard's order. Do not fabricate measurements; omit unavailable metrics or clearly identify their availability.
- Each visibility control must change the actual overlay and persist. Fast-forward and touch-control controls are conditional capabilities; touch controls obey physical-controller precedence.
- Touch, D-pad, A/confirm, B and Android Back must work interchangeably without losing focus to a scrim, Android wrapper or rendering surface. The Android wrapper must allow Compose descendants to own focus. Empty panel taps must not fall through to the outside-dismiss scrim.
- Restore the originating row on return. Deduplicate only the paired B/Back events from one physical press; a press closes exactly one navigation level and dismissal resumes exactly once.

## Firmware import

- Describe required firmware as a consumer-owned set, whether one file or several. Show the status of each required item and allow missing items to be added without replacing valid ones unnecessarily.
- For multi-file systems, allow selecting the required files together or separately. Validate the consumer's supported names, sizes and formats before atomically replacing firmware; normalize accepted filename case consistently.
- Distinguish required console firmware from unrelated arcade/system data when a core supports several platforms. Show progress and recoverable errors. Never bundle proprietary BIOS or game data with the shared framework or app.

## Integrated cheats and achievements

- Use `CheatsScreen` and a `CheatsBackend`; the consumer supplies a licensed catalog for its own system when available, its real parser, persistence and native application. Support an offline catalog where redistribution permits it; never reuse another console's codes or memory layout.
- Identify matching titles/regions/revisions conservatively. Do not automatically apply a different region's code when the catalog match is ambiguous. Explain missing or unsupported entries.
- Database/file import preserves manual cheats and core compatibility patches, skips duplicates and validates operations against that core's supported formats and memory bounds. New imported cheats start disabled, with clear counts/feedback. Native patches needed for game compatibility must not become removable user cheats.
- Keep manual creation and supported file import available. Treat native RAM-operation formats separately from textual GameShark/Action Replay formats; do not reinterpret an unsupported file as another console's cheat syntax.
- Respect achievement hardcore restrictions both in UI and native/core APIs. Use shared achievement settings, lists, notifications and the `RaBackend`; optional casual offline behavior uses GBFrame's cache/queue rules, with no fabricated unlock success or offline hardcore claims.
- Bundle catalog provenance, revision, attribution and redistribution license. Keep privacy documentation consistent with actual network/storage behavior. Feature parity does not imply that every catalog entry or external service has been device-validated.

## Local validation and adoption

- Validate locally first: relevant automated tests, build/static checks and device checks where available. Distinguish compiled, unit-tested, rendered and actually exercised behaviors in the result. Documentation is not proof of implementation.
- Wake the connected device during active use and turn its screen off afterward. Preserve installation data and signing compatibility; do not uninstall an app to force a differently signed update. Restore preferences, orientation and display overrides changed for testing.
- Do not publish or trigger remote builds merely for local iteration. Follow the user's publication instructions; local framework commits used to pin consumer submodules are distinct from uploading to GitHub.
- Update each consumer's GBFrame pin deliberately and audit its adapters. Track any remaining consumer gap explicitly instead of describing an untested implementation as conforming.
- Acceptance checks cover last-opened startup, direct/fallback launch, cover apply/Back, firmware validation, save thumbnail/errors, manual versus automatic slots, latest/exact/fresh loading, lifecycle pause preservation, mixed input, display modes, overlays and supported cheat import/toggle/deduplication. Test alternate palettes and compact/square/portrait layouts as applicable.
