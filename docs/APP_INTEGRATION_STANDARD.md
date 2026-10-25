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

The consuming app owns its artwork in its own Android resources; GBFrame must not embed a particular app's logo.

- Keep an app brand icon in the app's resources. Use the launcher icon painter when appropriate, or a dedicated in-app vector/drawable with the same brand artwork when the launcher asset is adaptive or unsuitable for inline display.
- Pass the same in-app brand artwork to `HomeTopBar`, `HomeMenuPanel`, and `AboutPage`. Keep the displayed app name consistent across those components.
- GBSat uses `R.mipmap.ic_launcher` in the top bar, main menu, and About page. Other apps may use a dedicated resource, but it must represent the same app identity in all three places.
- Do not leave an app with a generic core icon or omit its identity from the top bar or About page.

## CI artifact names

GBFrame does not build or name consumer APKs; each app's workflow must follow this filename convention:

- Development APK and uploaded Actions artifact: `<AppName>-v<versionName>-build<runNumber>`. The APK filename is `<AppName>-v<versionName>-build<runNumber>.apk`, and the uploaded artifact uses the same stem.
- Numbered release APK: `<AppName>-v<releaseVersion>.apk`. The release version is the app's public version; development build numbers do not replace it.
- Use the app's packaged Android `versionName` in the filename. Keep `versionCode` unique and monotonically increasing for updates.
- Do not expose a core or template filename such as `app-release.apk` or `flycast-release.apk) as the user-facing result.

For example, a development build can be `GBSat-v1.3.6-build402.apk`; its Actions artifact is named `GBSat-v1.3.6-build402`.

## Shared versus app-owned code

Reuse GBFrame components for shared screens, menu state, navigation, controller capture, and Back behavior. The app supplies only its real core actions, persistence, metadata, brand resources, and adapters such as `RaBackend` or `CheatsBackend`. When a shared helper documents dispatch order or lifecycle, follow that contract rather than adding a parallel app-local state machine.
