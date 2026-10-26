# GB family code review — 2026-10-26

Scope: static review of the shared GBFrame Kotlin UI and build configuration, plus the Android UI/build/workflow integration in GBSat and GBCast. This is a targeted static review, not a complete native-core audit or an on-device test.

## Findings requiring attention

### P1 — GBCast build workflow edits Gradle source with exact text substitutions
`GBCast/.github/workflows/android.yml` uses `sed` to replace the literal `signingConfigs.getByName("release")` and `versionCode = 8` in `flycast/build.gradle.kts`. The version substitution silently stops applying when the version code changes in source; it also couples CI to exact Kotlin DSL formatting. Prefer a Gradle property/environment variable for CI version code and a dedicated CI signing configuration/property instead of rewriting tracked build files in-place.

### P1 — Quick-menu pause/resume behavior is implemented separately in each app
GBSat's `GameScreen.kt` and GBCast's `GbcastGameUi.kt` both compose the GBFrame `QuickMenuHost` and use `QuickMenuPage`, `QuickMenuItemRow`, and full-screen Cheats/Achievements pages. They still own separate root page IDs, page lists, pause state transitions, and core actions. Keep core-specific JNI/actions in each app, but move any genuinely identical generic page/interaction primitives to GBFrame only when both adapters can express them without conditionals tied to a console.

### P1 — Quick-menu close must be idempotent across all dismissal paths
GBFrame's `QuickMenuHost` invokes `onDismiss` for scrim, close, and root Back. App callbacks resume their core. Both apps must ensure repeated Back/key events or concurrent native pause-state callbacks cannot resume twice or leave the CPU paused behind a dismissed menu. The existing standard calls this out; retain a regression checklist and test touch, gamepad, keyboard, system Back, scrim and close paths.

### P2 — Workflow parity and validation differ between repositories
GBSat has a longer Android workflow with NDK/CMake/DXC/cache/signing, Gradle profiling and 16 KB page-alignment checks. GBCast's Android workflow is much shorter and uploads an APK but does not visibly run the same alignment check or Gradle profiling. These differences may be legitimate due to the native cores, but define a shared minimum validation contract in GBFrame and implement app-specific steps as adapters rather than forcing identical native build commands.

### P2 — Shared app-ID checker is currently a template, not a directly reusable script
`GBFrame/tools/check-app-id.sh` hardcodes GBSat paths and filenames (`apps/android/app`, `tools/gbsat.bat`, `tools/gbsat.ps1`). Copying it into another repo without adjusting paths produces misleading failures. Either parameterize repository-specific paths/variable names, or explicitly label it as a GBSat-specific example and keep each app's wrapper thin. Each repo should run its own checker in CI on relevant Gradle/manifest/CMake/device-tool changes.

## Patterns to standardize in GBFrame

1. **App identity:** Gradle `namespace == applicationId`; native `APP_ID`, device scripts, manifest launcher component, external-media paths and APK naming must be checked against the same ID. Source-language package names are allowed to differ when manifest/JNI declarations are correct.
2. **Shared Compose shell:** app library, settings/About, theme tokens, focus restoration, Back handling and generic controller interactions belong in GBFrame. App modules provide their icon painter, name, settings entries and core callbacks.
3. **Compose-safe icon resources:** the app supplies a raster-compatible painter for Compose; never assume an adaptive launcher XML can be loaded by `painterResource`. Keep launcher assets separate from inline UI assets when required.
4. **Quick-menu host:** standardize panel, scrim, navigation stack, Back semantics, focus restoration, row presentation and themed colors. Keep core-specific capabilities and JNI calls in each app. Only expose actions that are actually implemented.
5. **Pause/resume lifecycle:** menu open pauses once; every dismissal route resumes once; leaving the game or activity lifecycle must not strand a paused core. Use explicit state transitions rather than scattered toggles where possible.
6. **Game library behavior:** game scanning and filesystem work run off the main thread; loading/error/empty states are explicit; launching prevents duplicate taps and always clears its loading state on success or failure/timeout.
7. **Async/focus retries:** retry focus only while a target is not attached/focusable, with a bounded timeout. Avoid unbounded loops and arbitrary delays as the only correctness mechanism.
8. **Build automation:** avoid `sed` rewrites of tracked source as build inputs; pass CI-specific values through Gradle properties/environment. Keep release signing secrets out of debug/CI paths.
9. **Minimum CI gates:** compile the shared GBFrame library first, then build each consumer against the updated submodule; run app-ID consistency checks, unit tests/lint where configured, native packaging checks (including 16 KB alignment where relevant), and upload the exact APK artifact. Report build success separately from device launch/runtime verification.

## Dead-code cleanup status

No source files or functions were deleted during this static pass. Simple text-based unused-import checks did not identify a reliable cleanup target; wildcard imports and references across composable/lambda code make text matching insufficient proof. Before deleting code, run Kotlin/Android lint or compiler diagnostics in each project, then verify the symbol has no reflective/JNI/XML/manifest/build-script use. This avoids removing native entry points or Android resources that are not referenced like ordinary Kotlin symbols.

## Verification limits

- GitHub source and workflow configuration were inspected statically.
- Build status is available separately in GitHub Actions; a successful build does not prove runtime behavior on a device.
- This pass did not execute an emulator/device session, native runtime tests, full lint, or a full dependency-aware dead-code analysis.
