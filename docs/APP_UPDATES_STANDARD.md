# App updates standard

Every app built on the kit checks for updates through `gb.frame.AppUpdates` (`AppUpdates.kt`). An app supplies only
`AppUpdateConfig(githubRepo = "owner/name")` and two Gradle manifest placeholders. Package names and signing keys do
not change.

## Channels

| Channel | How it is identified | Updater |
| --- | --- | --- |
| Play | installer package is exactly `com.android.vending` | Google Play In-App Updates (flexible flow) |
| GitHub | everything else: APKs marked with manifest metadata `gb.frame.updateChannel=github`, and unmarked installs from ADB, file managers, browsers or unknown sources | latest release of the official repository |

The only official channels are these two, so an unidentified install is GitHub. ADB and unknown sources are never
reported as Play.

## GitHub flow

1. `GET /repos/{owner}/{repo}/releases/latest`; the tag (`v1.2`) is compared with the installed `versionName`
   (`1.0 (build 5)` is read as `1.0`).
2. The first `.apk` asset is offered. Before installing, the kit downloads it to `cache/updates` and checks:
   package name equals the installed app, signing certificates equal the installed ones, and SHA-256 when the release
   publishes one (the asset `digest` field, or a `<apk>.sha256` asset).
3. Installation goes through `PackageInstaller`, so Android always shows its own confirmation. The user is first sent
   to "Install unknown apps" for the app when needed.

## Play flow

`AppUpdateManager` reports an available update; the kit prefers the flexible flow and uses the immediate flow only when
Google Play does not allow flexible updates. If Play reports an update but offers neither in-app flow, the dialog opens
the app's Play Store page instead of reporting that it is up to date. A failed update-flow task is surfaced in the UI.
When a flexible download finishes, the kit asks Play to complete the update.

## UX rules

- `AppUpdatePrompt(config)` goes once in the app root (library screen, not during gameplay). It checks once per process
  and only shows a dialog when an update exists; errors and "up to date" stay silent.
- The manual "Check for updates" row lives on the About page: pass `updateConfig = ...` to `AboutPage` and the kit
  adds it; it reports up to date, errors and failures.
- Network errors, unknown channel and a release without an APK never throw; the dialog offers the release page.
- All update prompts, results, actions and version labels use `KitText`, so English, Portuguese and Spanish follow the
  language selected in the app. GitHub release notes are not copied into the dialog because they are publisher text and
  are not localized by the kit.

## App integration

```kotlin
// build.gradle.kts, inside defaultConfig
val gbChannel = providers.gradleProperty("gbChannel").getOrElse("github")
manifestPlaceholders["gbUpdateChannel"] = gbChannel
manifestPlaceholders["gbInstallPermission"] =
    if (gbChannel == "play") "android.permission.INTERNET" else "android.permission.REQUEST_INSTALL_PACKAGES"
```

- Build the Google Play bundle with `-PgbChannel=play`. Play restricts `REQUEST_INSTALL_PACKAGES`, so the play build
  swaps it for `INTERNET`. APKs for GitHub use the default (`github`).
- The release asset must be published with the tag `v<version>` that matches `apps/android/VERSION`.
- Apps with a `.dev` application id suffix (non-release key) fail the package check on purpose: dev builds do not
  self-update from official releases.
