# GB-family in-game quick menu standard

All GB-family Android emulators must use the **GBSat in-game quick menu as the reference standard** for visual structure, hierarchy, controller interaction and Back behavior. New apps should reuse this pattern rather than inventing a different quick-menu design.

This standard describes a shared user experience, not a requirement to expose unsupported core features. Keep the layout and navigation consistent; include a setting only when the app/core can actually implement it.

The [shared behavior standard](SHARED_BEHAVIOR_STANDARD.md) is normative for settings placement, monitoring, firmware, cheat catalogs, automatic states and local validation. Its explicit cross-app requirements take precedence over an older app-specific reference implementation.

The reference implementation lives in [`gbframe/src/main/java/gb/frame/QuickMenu.kt`](https://github.com/mindspotgb/GBFrame/blob/main/gbframe/src/main/java/gb/frame/QuickMenu.kt). Apps should construct `QuickMenuPage` objects and use `QuickMenuHost`, `QuickMenuItemRow` and `QuickMenuItemContainer`; do not create a separate panel or Back/focus stack in each app. The app still owns each real core action and builds a capability-aware set of pages and rows.

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
- A panel entering from the left, occupying the full game-window height from the top edge to the bottom edge (in portrait: 72% of the height, aligned to the top, so a phone held upright keeps its picture and pad visible). Calculate its width from the Compose window constraints in dp: about 68% in portrait (capped at 320 dp), about 45% on phones in landscape, and about 46% on landscape 4:3 screens, capped at 400 dp and never wider than the window. This keeps labels readable on phones while leaving game area visible in landscape. Insets for system bars or cutouts may protect the panel's content horizontally, but must not shorten the panel vertically; the game window is immersive. Round the top-right and bottom-right corners and use a subtle border.
- A header showing a pause icon and **Paused** on the root page, with the current game's title beneath it. In a submenu, replace the pause icon with a Back arrow and show the submenu title. A close control dismisses the menu.
- A divider below the header and a vertically scrollable list of menu rows. On compact phone windows, use tighter header/row spacing and stack rows from the top; retain scrolling instead of spreading a short root list across the full panel height.
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

Use this order for common Video controls:

1. **Display**, cycling in this order: 4:3, 16:9, Fullscreen (omit unsupported
   choices while retaining their relative order).
2. **Widescreen hack**, only when the current display is Fullscreen or 16:9
   and the current game has a matching hack.
3. **Extended screen**, as a separate GBFrame toggle after Widescreen hack,
   only when the selected Display mode supports it (when the core has this capability).
4. **Renderer**.
5. **Resolution**.
6. **Filter**.
7. **Shader**.
8. Core-specific controls, including texture filtering (when available), VSync
   and renderer features. If the shared output filter is removed for a core,
   its texture filter may occupy item 5 with the shared **Filter** label; do not
   show a second texture-filter row later in the menu.

Use GBFrame's `ShaderSettingsRow`/`ShaderQuickMenuItem` and
`PictureFilterSettingsRow`/`PictureFilterQuickMenuItem` for the shared presenter.
The presenter filter acts after the core has drawn the frame; keep a separate
**Texture filtering** control when a core exposes texture sampling, unless the
app uses that control in place of the presenter filter. The standard choices
are **Nearest** and **Bilinear**, with Bilinear as the shared default.
Use GBFrame's `DisplayModeQuickMenuItem`, `SuperWidescreenQuickMenuItem`, `RendererQuickMenuItem`, and
`EnhancedResolutionQuickMenuItem` for these standard rows. Apps provide supported
choices and callbacks; GBFrame provides consistent row presentation and cycling.
Use the label **Resolution** in settings and in-game menus. Use
`EnhancedResolutionQuickMenuItem` for the shared presentation of resolution
choices; the app supplies the values and labels supported by its
renderer. This is a common control even when the cores use different units or
supported scales.

Only show dependent options when they work with the active renderer. When a choice changes, apply it to the running game, persist it where appropriate and update the displayed value immediately.

Use one mutually exclusive display-mode choice, shared with main Video settings. Include only supported modes; distinguish original aspect, 16:9, device-ratio expanded view and Fullscreen stretch. Clear incompatible mode flags when changing the choice. Monitoring belongs in Overlays: do not duplicate Show FPS here. Do not expose Rotate in quick-menu Video; respect device orientation and retain any supported core-specific rotation setting in main settings.

### Audio

The standard controls come from the kit: implement `AudioSettings` (volume 0..100, fast-forward audio mode) over the core and
call `AudioQuickMenuRows(settings)` first. The quick menu uses a volume slider plus 10% decrease/increase buttons (also
available with left/right on a controller) and a fast-forward audio choice. Settings → Audio uses
`AudioSettingsGroups(settings)` with the same 10% buttons, focusable by physical controllers, alongside the touch slider.

- **Volume**, adjusted with the slider or 10% buttons and applied immediately.
- **Fast-forward audio**: Default (normal pitch, in pieces), Speed up (faster and higher, following the game speed),
  Mute. The core must honor all three, and must not wait for the audio output while fast-forwarding.

Then the app's own audio options (GBCast: DSP, latency, buffer, VMU sound), after the standard ones.

### Order of standard and app options

In every settings page and quick-menu page, the kit's standard options come first and the app's own (core-specific)
options after them. Put the app's options in their own group in settings (e.g. "Sound chip (AICA)").

### Performance

Expose the applicable GBSat-style controls in this order:

- **Auto frame skip** (On/Off when the core supports adaptive render skipping). Use GBFrame's `AdaptiveGraphicsItem`; the consumer supplies and persists the setting. When On, it skips rendering below the speed target, preserves normal audio pacing, and remains independent of Fast Forward.
- **Dynarec** when supported. Use GBFrame's `CpuRecompilerSettingsRow` and `CpuRecompilerQuickMenuItem`; the app supplies the live core value.
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
Keep core texture sampling separate from GBFrame's final picture scaling filter,
unless the app intentionally uses the core filter as its only Filter option.
Use `AutoFrameSkipSettingsRow`/`AutoFrameSkipQuickMenuItem` when the core
implements the Normal/Max policy represented by the shared labels. Dreamcast
cable and broadcast configuration stay automatic: keep the core's normal
defaults and override only for confirmed game-specific requirements. Region
and expanded-RAM policy are automatic core behavior and must not be exposed as
user toggles.

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

Use this order: **Cheats**, **Achievements**, **Swap disc**, **Reset**, followed
by any additional core-specific session controls. Render Reset with the shared
`ResetQuickMenuItem`; apps provide only the core-specific callback. Keep shared
destinations first and app/core actions after them.
Use the GBSat pattern for session/game-specific actions:

- **Cheats** — use the shared GBFrame `CheatsScreen` with an app/core-specific `CheatsBackend`. Every GB-family app should use this component; the adapter owns code validation/parsing, database lookup, per-game persistence and applying cheats to the core. If the core cannot apply cheats, keep the shared screen and clearly report the limitation rather than exposing fake controls.
- **Achievements** — use the shared GBFrame `AchievementsScreen` and `RetroAchievements` UI with the app's `RaBackend` adapter. Keep the menu entry available when the app has initialized the backend, even if RetroAchievements is disabled or the user is not logged in; the screen/settings should explain how to enable or sign in rather than hiding the feature.
- After **Reset**, include additional supported core/session actions such as
  per-game overrides and native advanced settings.

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

The currently shared controls visible in both GBSat and GBCast are widescreen
game hacks, shader, output filter, Dynarec, audio volume/fast-forward audio,
monitoring, touch controls, resolution, and CD read speed. Their UI,
labels, cycling behavior and explanations belong in GBFrame. Apps provide the
core values, capability checks, persistence and callbacks. Keep Texture filtering
as a separate GBFrame control when the core offers texture sampling. Auto frame
skip (On/Off on GBSat; Off/Normal/Max on GBCast) uses separate GBFrame
components with different core policies and settings. Apps supply
capabilities and supported values for display mode and renderer choices.
Place **Recommended settings per game** under System > **Per-game profiles**;
profiles can affect video and performance, so keep the toggle outside the
renderer-only group.

For cores integrated with the GBFrame Skip Engine, render its three optional
controls with `SkipEngineQuickMenuRows`; the app provides the persisted values
and callbacks. Omit this helper when the core cannot detect load, transition and
save-wait states reliably.

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
