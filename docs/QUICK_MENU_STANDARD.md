# GB-family in-game quick menu standard

All GB-family Android emulators must use the **GBSat in-game quick menu as the reference standard** for visual structure, hierarchy, controller interaction and Back behavior. New apps should reuse this pattern rather than inventing a different quick-menu design.

This standard describes a shared user experience, not a requirement to expose unsupported core features. Keep the layout and navigation consistent; include a setting only when the app/core can actually implement it.

The reference implementation lives in [`gbframe/src/main/java/gb/frame/QuickMenu.kt`](https://github.com/mindspot7/GBFrame/blob/main/gbframe/src/main/java/gb/frame/QuickMenu.kt). Apps should construct `QuickMenuPage` objects and use `QuickMenuHost`, `QuickMenuItemRow` and `QuickMenuItemContainer`; do not create a separate panel or Back/focus stack in each app. The app still owns each real core action and builds a capability-aware set of pages and rows.

## Visual layout

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

- Show the core's available save-state slots in a consistent list, with screenshot thumbnails when available, slot number, saved date/time or an Empty label.
- Loading an empty slot must be disabled.
- Execute save/load work off the UI thread where appropriate; show a recoverable error in the menu if an operation fails.
- After successful save/load, close the quick menu and resume the session, matching GBSat.
- If the core does not support save states, do not show fake controls. Document the limitation and omit these rows until the feature exists.

### Video

Follow GBSat's hierarchy and expose available options in this order:

- **Display**: aspect/display mode such as 4:3, Fullscreen and 16:9, if supported by the core and frontend.
- **Renderer**: cycle/select among the renderers the app actually implements.
- **Enhanced resolution**: show only when the active renderer supports an internal-resolution scale; display the real current value.
- **Shader**: cycle through the supported shader choices, if applicable.
- **Filter**: cycle through supported image filters, if applicable.

Only show dependent options when they work with the active renderer. When a choice changes, apply it to the running game, persist it where appropriate and update the displayed value immediately.

### Audio

Use the GBSat pattern where the backend supports it:

- **Volume down** and **Volume up**, showing the current volume and using bounded increments.
- **Fast-forward audio**, with the supported choices (for example Default, Speed up and Mute).

Clamp values to valid ranges and apply changes to the active session. Do not display options the audio backend cannot honor.

### Performance

Expose the applicable GBSat-style controls in this order:

- **Frame skip** (Auto/Off, or the actual supported modes).
- **Threaded audio** (Auto/On/Off only if supported).
- **Skip loading screens**.
- **Skip transitions**.
- **Skip save waits**.

The last three are conditional capabilities: include them only if the app has a tested equivalent to the GB family's Skip Engine and can reliably detect those situations. Do not fake or approximate them with unconditional fast-forward.

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

### Game

Use the GBSat pattern for session/game-specific actions:

- **Reset** — reset the current game after performing any required confirmation.
- **Disc tray** — expose real open/close or disc-switching behavior when the core supports a disc tray or equivalent multi-disc operation.
- **Cheats** — open the app's actual cheat UI only if supported.
- **Achievements** — open the app's actual achievement UI only if supported.

Keep nested screens such as Cheats and Achievements inside the quick-menu navigation stack. Back from a nested screen must return to the Game submenu, not dismiss the whole quick menu.

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
- D-pad navigation, confirm, Back, touch and keyboard access must work consistently. Avoid arbitrary one-off delays as the only focus-restoration mechanism; follow GBFrame's existing focus-retry patterns.
- Closing the menu resumes emulation exactly once. Repeated or synthetic Back events must not accidentally close multiple navigation levels or resume twice.

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

In real code, keep the standard root order from this document, and create submenu entries only when the app capability model confirms that the action is implemented. Use `fullScreen = true` for existing full-window subpages such as cheat/achievement screens that still need to participate in the same Back stack.
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
