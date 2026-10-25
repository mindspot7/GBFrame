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
- Use GBFrame's `emulatorLabel(manufacturer, console)` and shared `AboutPage`; do not fork the About layout.
- Provide app-specific credits and license text to `AboutPage`.

## App icon and in-app identity

The consuming app owns its launcher icon in its own Android resources; GBFrame must not embed a particular app's logo.

- Use the app's launcher icon artwork in `HomeTopBar`, `HomeMenuPanel`, and `AboutPage`. These three places must show the same icon identity as the installed app.
- Pass the same icon resource/painter to all three components when possible. If an adaptive launcher asset cannot render inline, use an inline-compatible asset derived from the exact same icon artwork; do not substitute a different logo or core icon.
- Keep the displayed app name consistent across the top bar, main menu, and About page.
- GBSat uses `R.mipmap.ic_launcher` for all three. Each app owns and supplies its own icon resource.
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

## Shared versus app-owned code

Reuse GBFrame components and helpers for shared screens, menu state, navigation, controller capture, library shortcuts, and Back behavior. The app supplies real core actions, persistence, metadata, its launcher icon resources, and adapters such as `RaBackend` or `CheatsBackend`. When a shared helper documents dispatch order or lifecycle, follow that contract rather than adding a parallel app-local state machine.
