# GB-family in-game quick menu standard

All GB-family Android emulators must use the **GBSat in-game quick menu as the reference standard** for visual structure, hierarchy, controller interaction and Back behavior. New apps should reuse this pattern rather than inventing a different quick-menu design.

This standard describes a shared user experience, not a requirement to expose unsupported core features. Keep the layout and navigation consistent; include a setting only when the app/core can actually implement it.

The [shared behavior standard](SHARED_BEHAVIOR_STANDARD.md) is normative for settings placement, monitoring, firmware, cheat catalogs, automatic states and local validation. Its explicit cross-app requirements take precedence over an older app-specific reference implementation.

The reference implementation lives in [`gbframe/src/main/java/gb/frame/QuickMenu.kt`](https://github.com/mindspot7/GBFrame/blob/main/gbframe/src/main/java/gb/frame/QuickMenu.kt). Apps should construct `QuickMenuPage` objects and use `QuickMenuHost`, `QuickMenuItemRow` and `QuickMenuItemContainer`; do not create a separate panel or Back/focus stack in each app. The app still owns each real core action and builds a capability-aware set of pages and rows.

## Visual layout

Expose **Show all monitoring** and **Show fast-forward button** in main Video
settings and quick-menu Overlays. The first toggles FPS, speed, CPU, GPU, renderer,
resolution and frame time together. When the on-screen fast-forward button is
enabled, omit the duplicate fast-forward action from the quick-menu root.

Android Back must be handled once per press, including key DOWN/UP and system
navigation gestures. A press during gameplay opens and retains the paused quick
menu; subsequent Back presses traverse its shared page stack before dismissing.

Use the same basic GBSat arrangement:

- A dimmed, tappable scrim over the game; tapping outside the panel dismisses the quick menu and resumes emulation.
- A panel entering from the left, occupying the full available height and approximately 85% of the width, capped at about 340 dp on wider screens. Round the top-right and bottom-right corners and use a subtle border.
- A header showing a pause icon and **Paused** on the root page, with the current game's title beneath it. In a submenu, replace the pause icon with a Back arrow and show the submenu title. A close control dismisses the menu.
- A divider below the header and a vertically scrollable list of menu rows.
- Each row uses a meaningful icon, a concise label, and—where relevant—the current value aligned on the trailing side. Rows that open submenus show a disclosure arrow.
- The focused row must have a clear highlight suitable for gamepad navigation.

Use GBFrame's palette/theme tokens for the app's color identity while preserving this shared shape, spacing, hierarchy and behavior. Do not hardcode the GBSat violet/gold colors into every app.

## Root menu: standard order

Use this order for the root page:

1. **Resume** — dismiss the quick menu and resume the current game.
2. **Save state** — open save-state slots.
3. **Load state** — open load-state slots.
4. **Fast-forward** — toggle fast-forward. Hide this row when an equivalent on-screen fast-forward button is enabled, as GBSat does.
5. **Video** — open video/display options.
6. **Audio** — open audio options.
7. **Performance** — open performance options.
8. **Overlays** — open performance overlay and touch-control options.
9. **Game** — open game/session actions and supported extras.
10. **Exit** — exit the current game cleanly and return to the library. This does not mean force-quitting the app.

Do not reorder these standard rows or rename them without a clear usability reason. If a capability is unavailable, omit only the unsupported row or option; preserve the ordering of the remaining rows.

## Standard submenu contents

### Save state and Load state

- Expose no more than `SaveData.MANUAL_SAVE_STATE_SLOTS` numbered/manual slots (five by default), in order from slot 1. If a core supports fewer, show only the supported slots. Do not expose extra numbered slots just because the core supports them.
- Use the shared `SaveData.MANUAL_SAVE_STATE_SLOTS` constant when building slot lists; do not duplicate a per-app count.
- Show screenshot thumbnails when available, slot number, saved date/time or an Empty label. Loading an empty slot must be disabled.
- Render each slot with GBFrame's `QuickMenuStateSlot`: an 88 x 66 dp screenshot with rounded corners, followed by the slot label and localized date/time. Core adapters supply the timestamp and decoded thumbnail; existing states without screenshots use the image placeholder.
- Quick-save, auto-save and other automatic states are separate special features, not extra numbered/manual slots.
- Execute save/load work off the UI thread where appropriate; show a recoverable error in the menu if an operation fails.
- Capture the game screenshot on the thread/context required by the renderer and refresh the slot only after the operation completes. Keep special automatic slots separate from the manual-slot preference and its bounds.
- After successful save/load, close the quick menu and resume the session, matching GBSat.
- If the core does not support save states, do not show fake controls. Document the limitation and omit these rows until the feature exists.

### Video

Follow GBSat's hierarchy and expose available options in this order:

- **Display**: aspect/display mode such as 4:3, Fullscreen and 16:9, if supported by the core and frontend.
- **Renderer**: cycle/select among the renderers the app actually implements.
- **Enhanced resolution**: show only when the active renderer supports an internal-resolution scale; display the real current value.
- **Shader**: cycle through the supported shader choices, if applicable.
- **Filter**: cycle through supported image filters, if applicable.

Use GBFrame's `ShaderSettingsRow`/`ShaderQuickMenuItem` and
`PictureFilterSettingsRow`/`PictureFilterQuickMenuItem` for the shared presenter.
The presenter filter acts after the core has drawn the frame; keep a separate
**Texture filtering** control when a core exposes texture sampling. The standard
choices are **Nearest** and **Bilinear**, with Bilinear as the shared default.

Only show dependent options when they work with the active renderer. When a choice changes, apply it to the running game, persist it where appropriate and update the displayed value immediately.

Use one mutually exclusive display-mode choice, shared with main Video settings. Include only supported modes; distinguish original aspect, 16:9, device-ratio expanded view and Fullscreen stretch. Clear incompatible mode flags when changing the choice. Monitoring belongs in Overlays: do not duplicate Show FPS here. Do not expose Rotate in quick-menu Video; respect device orientation and retain any supported core-specific rotation setting in main settings.

### Audio

The standard rows come from the kit: implement `AudioSettings` (volume 0..100, fast-forward audio mode) over the core and
call `AudioQuickMenuRows(settings)` first; Settings → Audio uses `AudioSettingsGroups(settings)` the same way.

- **Volume down** and **Volume up**, showing the current volume, 10% steps.
- **Fast-forward audio**: Default (normal pitch, in pieces), Speed up (faster and higher, following the game speed),
  Mute. The core must honor all three, and must not wait for the audio output while fast-forwarding.

Then the app's own audio options (GBCast: DSP, latency, buffer, VMU sound), after the standard ones.

### Order of standard and app options

In every settings page and quick-menu page, the kit's standard options come first and the app's own (core-specific)
options after them. Put the app's options in their own group in settings (e.g. "Sound chip (AICA)").

### Performance

Expose the applicable GBSat-style controls in this order:

- **Adaptive graphics / frame skip** (Auto/Off when the core supports adaptive render skipping). Use GBFrame's `AdaptiveGraphicsItem`; the consumer supplies and persists the setting. It skips rendering below the speed target, preserves normal audio pacing, and remains independent of Fast Forward.
- **CPU recompiler (Dynarec)** when supported. Use GBFrame's `CpuRecompilerSettingsRow` and `CpuRecompilerQuickMenuItem`; the app supplies the live core value.
- **Threaded audio** (Auto/On/Off only if supported).
- **Skip loading screens**.
- **Skip transitions**.
- **Skip save waits**.

The last three are conditional capabilities: include them only if the app has a tested equivalent to the GB family's Skip Engine and can reliably detect those situations. Do not fake or approximate them with unconditional fast-forward.

Use `FastCdLoadSettingsRow`/`FastCdLoadQuickMenuItem` for a core's one-step
accelerated CD transfer setting and `CdReadSpeedSettingsRow`/
`CdReadSpeedQuickMenuItem` for a separate drive-speed multiplier. Keep both when
the core implements distinct mechanisms; apps provide only the supported values.
Apply `RecommendedSettingsPerGameRow` in main Video settings when an app ships a
measured `game_profiles.json` and honors user per-game choices before profiles.

For video settings, use `RendererSettingsRow`, `EnhancedResolutionSettingsRow`,
`TextureFilterSettingsRow`, `ShaderSettingsRow`, and `PictureFilterSettingsRow`.
Keep core texture sampling separate from GBFrame's final picture scaling filter.
Use `AutoFrameSkipSettingsRow`/`AutoFrameSkipQuickMenuItem` when the core
implements the Normal/Max policy represented by the shared labels. Dreamcast
system settings use `DreamcastRegionSettingsRow`, `DreamcastCableSettingsRow`,
`DreamcastBroadcastSettingsRow`, and `DreamcastRamModSettingsRow`; apps still
map their returned values to core/JNI calls.

### Overlays

Offer supported visibility toggles in this order:

- **Show all monitoring**.
- **Show FPS**.
- **Show speed**.
- **Show renderer**.
- **Show resolution**.
- **Show CPU**.
- **Show GPU**.
- **Show frame time**.
- **Show fast-forward button**.
- **Touch controls**.

Each toggle must be connected to the actual overlay or input UI. Omit metrics the app cannot measure and touch controls when the app has no touch-control implementation. The "Show all monitoring" option should consistently enable/disable the supported monitoring metrics together.

Main settings **Video > Overlays** must expose the same supported monitoring preferences as **Quick menu > Overlays**, with one persisted source of truth. Render through GBFrame's shared monitoring/notification components and remove duplicate core-native FPS/pause/notification overlays. A distinct hardware display or control may remain when it is a real consumer capability.

### Game

Use the GBSat pattern for session/game-specific actions:

- **Reset** — reset the current game after performing any required confirmation.
- **Disc tray** — expose real open/close or disc-switching behavior when the core supports a disc tray or equivalent multi-disc operation.
- **Cheats** — use the shared GBFrame `CheatsScreen` with an app/core-specific `CheatsBackend`. Every GB-family app should use this component; the adapter owns code validation/parsing, database lookup, per-game persistence and applying cheats to the core. If the core cannot apply cheats, keep the shared screen and clearly report the limitation rather than exposing fake controls.
- **Achievements** — use the shared GBFrame `AchievementsScreen` and `RetroAchievements` UI with the app's `RaBackend` adapter. Keep the menu entry available when the app has initialized the backend, even if RetroAchievements is disabled or the user is not logged in; the screen/settings should explain how to enable or sign in rather than hiding the feature.

These are standard GB-family features, not opportunities to create separate per-app UI. The specific native/core implementation still belongs to the app. Keep nested screens inside the quick-menu navigation stack. Back from Cheats or Achievements must return to the Game submenu and restore focus to the row that opened it.

Auto save and Auto load are global controls in **main settings > System**, using `SaveStateSettings`; do not duplicate them in quick-menu Game. Cheat database behavior follows the shared standard: system-specific licensed catalog, conservative region/revision matching, preservation of existing/manual entries and compatibility patches, disabled imports, duplicate prevention and native hardcore enforcement.

## Navigation and controller contract

This behavior is mandatory:

- Opening the quick menu pauses emulation without destroying the active game session.
- Android system Back during gameplay opens the quick menu. Keep immersive mode; do not force the Android navigation bar to remain visible as a workaround.
- The app's configured menu hotkey and an in-game on-screen menu handle (when provided) open the same quick menu.
- Do not reinterpret the controller's normal gameplay B button as a menu command while the game is actively running. Keep system Back distinct from gamepad B and from duplicate synthetic Back events.
- In the quick menu, Back/B follows a stack: close a nested screen first, then return from a submenu to Root, then dismiss Root and resume the game.
- The first actionable row of every newly opened page receives focus so the menu works with a controller immediately.
- When returning from a submenu, restore focus to the exact Root row that opened it. On returning from a nested screen, restore focus to the row that opened that screen.
- The rendering SurfaceView must not steal keyboard/gamepad focus while the menu is open.
- Neither the outside-dismiss scrim nor a focusable Android wrapper may trap controller focus. Android ComposeView integrations must let Compose descendants own focus. Empty taps inside the panel must not dismiss through the scrim.
- D-pad navigation, confirm, Back, touch and keyboard access must work consistently. Avoid arbitrary one-off delays as the only focus-restoration mechanism; follow GBFrame's existing focus-retry patterns.
- Closing the menu resumes emulation exactly once. Repeated or synthetic Back events must not accidentally close multiple navigation levels or resume twice.

### Android background/foreground regression checks

The quick-menu pause contract also applies when Android temporarily backgrounds the activity. Follow the lifecycle rules in [APP_INTEGRATION_STANDARD.md](APP_INTEGRATION_STANDARD.md); do not implement a separate quick-menu lifecycle policy.

- With gameplay active and the quick menu closed, send the app to the background and return: gameplay must resume without requiring the user to open/close the menu.
- With the quick menu open, send the app to the background and return: preserve the open menu and its intentional pause; do not resume gameplay behind it.
- Repeat both scenarios several times, including a rapid app switch. There must be no stale resume flag, double toggle, duplicate resume, or permanently paused game after the menu is dismissed.
- Also verify the normal dismissal paths listed above (Resume, root Back/B, scrim, close control) still resume exactly once. Lifecycle recovery must not alter the quick-menu navigation stack.

## Shared API usage

The minimal shape is:

```kotlin
val videoPage = QuickMenuPage("video", "Video") {
    QuickMenuItemRow(
        id = "renderer", icon = Icons.Default.Tv,
        label = "Renderer", value = core.rendererLabel,
    ) { core.cycleRenderer() }
}
val rootPage = QuickMenuPage("root", "Paused") {
    QuickMenuItemRow(id = "video", icon = Icons.Default.Tv, label = "Video", hasSubmenu = true) {
        openPage(videoPage, returnFocusItemId = "video")
    }
    QuickMenuItemRow(id = "resume", icon = Icons.Default.PlayArrow, label = "Resume") { dismiss() }
}
QuickMenuHost(rootPage, gameTitle = currentGameTitle, onDismiss = resumeGame)
```

For Cheats and Achievements, create app-level pages that call the shared screens:

```kotlin
val cheatsPage = QuickMenuPage("cheats", "Cheats", fullScreen = true) {
    CheatsScreen(backend = appCheatsBackend, onBack = { back() })
}
val achievementsPage = QuickMenuPage("achievements", "Achievements", fullScreen = true) {
    AchievementsScreen(onBack = { back() })
}
```

The app must initialize `RetroAchievements` with its `RaBackend` adapter and provide `appCheatsBackend` as a `CheatsBackend` implementation. The backend handles core-specific code formats and native calls; the shared screen handles presentation, editing flow, file selection, controller-compatible navigation and feedback.

In real code, keep the standard root order from this document, and create submenu entries only when the app capability model confirms that the action is implemented. Use `fullScreen = true` for existing full-window subpages such as cheat/achievement screens that still need to participate in the same Back stack.

The shared quick-menu scrim and side panel fill the app window from top to bottom. Keep vertical system-bar insets off the panel content so the panel does not shrink differently between consumers; apply horizontal safe-drawing insets only for cutouts. The panel width is responsive to the available window, capped at 340 dp, and uses 85% of narrower windows.
## Implementation contract

The quick-menu visuals may be shared in GBFrame, but console/core operations belong to the app's adapter layer. Keep the separation clear:

- GBFrame owns the reusable layout, theming, row presentation, navigation stack conventions, focus restoration, and generic value/toggle interactions.
- Each app supplies a capability-aware model for its available menu groups/items, current values, and actions.
- Core-specific options and JNI calls remain in the app/core integration layer; GBFrame must not import a console core or hardcode console-specific behavior.
- Every visible item must invoke a working action and reflect the actual core state. Persist settings using the app's established preferences path.
- Optional features must be discoverable from an explicit capability model or well-defined app-provided menu items, not guessed from the app name or core brand.

## Acceptance checklist

Before considering the quick menu complete, verify on device or emulator:

- [ ] Root items appear in the standard order, with unsupported entries omitted rather than left inert.
- [ ] The game pauses on open and resumes on dismiss.
- [ ] Android Back, the menu hotkey and any on-screen menu button open the same panel.
- [ ] Back/B closes exactly one navigation level at a time.
- [ ] Focus is immediately usable and returns to the originating row after leaving each submenu.
- [ ] Video/audio/performance/overlay changes actually reach the running core and update displayed values.
- [ ] Save/load errors are visible and do not crash or silently discard the active session.
- [ ] The game can be exited to the library without force-quitting the app.
- [ ] Immersive mode and normal gameplay input are preserved.
- [ ] Tests distinguish build success from actual device-level controller validation.
- [ ] Touch followed by D-pad/A/B navigation retains usable row focus and never activates the scrim by confirm.
- [ ] Main Video/Overlays and quick-menu Overlays share persisted values; quick-menu Video has no Show FPS or Rotate.
- [ ] Auto save/Auto load appear only in main System settings, and special states preserve the manual-slot selection.
