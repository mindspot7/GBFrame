# GB family code review — 2026-10-26

Scope: static review of the shared GBFrame Kotlin UI and build configuration, plus the Android UI/build/workflow integration in GBSat and GBCast. This is a targeted static review, not a complete native-core audit or an on-device test.

## Findings requiring attention

### RESOLVED — GBCast CI no longer rewrites Gradle source
The Android workflow now passes `-PgbcastVersionCode` and `-PgbcastCiDebugSigning=true` to Gradle. The build script retains version code 8 as its local default and selects debug signing only when the CI property is explicitly enabled. This removes the brittle `sed` rewrites of tracked Gradle source. The workflow/build configuration was re-read during the 2026-10-26 follow-up; no new build was triggered solely for this documentation refresh.
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

## Follow-up — CI property refactor validated

The GBCast workflow was updated to pass `-PgbcastVersionCode` and `-PgbcastCiDebugSigning=true` rather than editing `build.gradle.kts` with `sed`. The Gradle script keeps version code 8 as its default for ordinary builds and switches to the debug signing config only when the CI property is explicitly true.

Validation completed successfully:
- GBCast build run #43: https://github.com/mindspot7/GBCast/actions/runs/40314936050
- GBCast App ID consistency check: https://github.com/mindspot7/GBCast/actions/runs/40314871597

A lightweight import-reference scan across the two game-screen and two library-screen files produced candidates, but some are false positives for Kotlin delegated-property operators (`getValue`/`setValue`) and symbols used indirectly or through extension syntax. No imports were removed based on that scan alone. Use Android Lint/compiler diagnostics or IDE semantic analysis before committing dead-code cleanup.

## Additional parity observation

Both game menus implement the same generic save/load slot page pattern with GBFrame's `QuickMenuPage` and `QuickMenuItemRow`, but the backing operations and state-file conventions differ. This is a candidate for a GBFrame slot-page abstraction only if it can accept app-provided slot availability, save/load callbacks, labels, and error handling; do not move native file-path assumptions into GBFrame. Both also expose renderer/resolution/audio/performance toggles, but their supported values and core APIs differ, so standardize row presentation and focus/navigation behavior rather than forcing identical settings models.

## Follow-up — pause/resume source trace

A source-level trace of the current app adapters and shared host found:

- **GBSat:** `Emulator.pause(p)` assigns the explicit target state to `paused` and calls `NativeBridge.nativeSetPaused(p)`; it is not a toggle. Repeated calls therefore do not invert the native pause state, although they do repeat play-time accounting and native calls. The current screen can reach this method from Back, the menu button, the hotkey request, menu dismissal, game launch, and close-game paths.
- **GBFrame:** `QuickMenuHost` owns the nested-page Back stack and invokes `onDismiss` at the root, on scrim, and on close. It deliberately does not pause/resume the core itself; each app adapter owns that responsibility.
- **GBCast:** `GbcastGameUi.resume()` hides the overlay and calls `JNIdc.guiTogglePause()` only when `JNIdc.guiState() == PAUSE`. That state guard makes repeated calls less likely to toggle a running game back into pause, but correctness depends on the native GUI state being current and the menu remaining in sync with it. `hideForNative()` is a distinct transition that hides Compose while handing control to Flycast's native settings state.
- **Validation status:** this is static source analysis only. No device/emulator session or native lifecycle test was run, so no runtime regression is confirmed.

### Recommended next change

Do not change pause semantics blindly. Add a small adapter-level transition guard and focused tests only after verifying the native contracts for `nativeSetPaused` and `guiTogglePause`. Cover rapid repeated Back, root Back versus nested Back, scrim/close, hotkey open, activity background/foreground, native settings return, Reset/Restart, and game exit. In GBSat, consider short-circuiting `pause(p)` when `paused == p` only after confirming there are no required side effects in `nativeSetPaused`; the current code is explicit-state, not toggle-based.


### Follow-up — GBCast native toggle contract verified

The native source trace is now complete for the GBCast pause toggle:

- `shell/android-studio/flycast/src/main/java/com/flycast/emulator/emu/JNIdc.java` declares `guiTogglePause()` as a native method.
- `shell/android-studio/flycast/src/main/jni/src/gbcast_jni.cpp` implements that JNI method as a direct call to `gui_togglePause()`; `guiState()` returns the current `gui_state`, and `guiSetState()` directly calls `gui_setState()`.
- `core/ui/gui.cpp` shows `gui_togglePause()` is a true toggle: when state is `Closed`, it stops the emulator and enters `Pause`; when state is `Pause`, it enters `Closed` and calls `emu.start()`. The function holds `guiMutex`, but the mutex serializes calls; it does not make duplicate sequential calls idempotent. Other states do not trigger either transition in this function.
- The Kotlin Back handler's fallback branch calls the toggle and then displays Compose. A repeated event can therefore be hazardous if the state/visibility transition is not synchronized as expected. This is a confirmed risk in the contract, not a confirmed reproduction of a user-visible bug.

**Decision:** no pause logic was changed in this pass. A blanket debounce or replacing the toggle with state writes would be unsafe without tracing every native GUI transition and lifecycle path. Next implementation step should make the Kotlin adapter's transition policy explicit (single owner of pause/resume, state checked before each action) and add testable decision logic for Back/state transitions; device testing is still required for native integration.

### Follow-up — GBSat native pause setter verified

The native side of GBSat's pause contract was inspected in `apps/android/app/src/main/cpp/gbsat_jni.cpp`:

- `nativeSetPaused(paused)` assigns the requested value to the atomic `g->paused`, clears the audio ring when pausing, and notifies `g->cmdCv`.
- This is an explicit-state setter, not a toggle. Repeated assignments do not invert the pause state, but they do repeat the audio-ring clear and condition-variable notification. Do not add an early return without checking whether repeated pause requests are expected to flush audio or wake the emulation thread.
- This further supports leaving the existing GBSat native behavior unchanged until lifecycle tests establish a need for a guard.

The source-level contract checks for both apps are now complete. The next safe change is a small, independently testable Kotlin transition-policy layer plus tests, followed by the native/device lifecycle matrix; no runtime test has been performed in this review.

### Follow-up — GBCast Back policy extracted and covered by unit tests

A small refactor was committed to GBCast on `android-port`:

- `GbcastBackPolicy.kt` isolates the state-to-action decision from Android/Compose and JNI side effects.
- `GbcastGameUi.onAndroidBack()` now dispatches through that policy while retaining the existing action handlers and native toggle semantics.
- `GbcastBackPolicyTest.kt` covers native submenus, command/settings/disk/cheat/achievement pages, Pause, Main, and unknown-state fallback.
- CI was changed to run `testReleaseUnitTest` before `assembleRelease`, so the new JVM tests are part of the validation build.

This is a decision-policy extraction, **not yet a duplicate-event debounce**. The existing toggle fallback is intentionally unchanged because the native GUI state contract is known but lifecycle/event ordering still needs device validation. GitHub Actions run #45 was queued at the time of this report update: https://github.com/mindspot7/GBCast/actions/runs/40318564412. Do not treat the tests or APK build as passed until that run completes successfully.


### Follow-up — device verification after the 16 KB validator fix (2026-10-26)

The user installed and tested the resulting GBSat build #361 and GBCast build #58 on an Android device and confirmed that both worked.

- GBSat build #361: https://github.com/mindspot7/GBSat/actions/runs/40337602238 — workflow succeeded; the shared GBFrame validator passed; artifact `GBSat-v1.3.6-build361` uploaded.
- GBCast build #58: https://github.com/mindspot7/GBCast/actions/runs/40338006960 — workflow succeeded; `:flycast:testDebugUnitTest` and `:flycast:assembleRelease` completed; the shared validator passed for the packaged native libraries across `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; artifact uploaded.
- The corrected validator is pinned to GBFrame commit `6fb96ecac381cf7613b7e3321ade39b73b052fd0` in both app workflows.
- This confirms the tested install/launch path as reported by the user and the CI build/package checks. It does not establish coverage of every pause/resume lifecycle path.

### Next implementation sequence

The user reports that the quick menus appear to work correctly in device testing. Avoid changing menu behavior without a reproducible defect. Priorities for the next static-analysis pass are:

1. Run Android Lint/Gradle diagnostics in each consumer repository where the environment and workflow permit; separate pre-existing baseline findings from regressions and do not delete symbols based on text search alone.
2. Review workflow triggers and validation parity, especially whether GBCast checks should run on relevant source changes rather than only the dedicated build trigger file.
3. Verify the app-ID consistency scripts against their manifests, Gradle configuration, and device tools, and ensure the GBFrame script remains explicitly documented as a GBSat-oriented template.
4. Keep native-core audit and broader device lifecycle scenarios out of the “verified” category unless those checks are actually run.


### Follow-up — foreground lifecycle behavior validated (2026-10-26)

The Android foreground/background resume fix was committed to GBSat and GBCast and the user manually tested both apps, reporting that the behavior worked. CI builds also succeeded:

- GBSat commit `fe47aceb79f81dcda9e3e1e6989dcb8bc19b31d8`; build #369: https://github.com/mindspot7/GBSat/actions/runs/40349125808
- GBCast commit `09d46c52c0fbb71267ca379e765e5cb8a532cabe`; build #59: https://github.com/mindspot7/GBCast/actions/runs/40348820064

GBFrame's integration, lessons, and quick-menu standards were updated to describe the lifecycle contract and regression cases. The menu code itself was not changed in this documentation follow-up.

### Follow-up — static review continuation (2026-10-26)

- Confirmed both consumer Gradle files declare `namespace == applicationId`.
- Confirmed both repositories have a dedicated app-ID consistency workflow.
- GBSat's checker is intentionally path-specific; GBFrame's copy is labeled as a template that must be adapted rather than copied unchanged.
- Re-read the GBCast Android workflow and Gradle file: CI passes version/signing values through Gradle properties and no longer edits the tracked build script with text substitutions.
- Repository code search did not provide reliable results for a comprehensive unused-symbol or hard-coded media-path audit. No code was removed based on these searches.
- Android Lint has **not yet been executed** in this pass. The workflows currently run targeted JVM tests/build/package checks; those results must not be represented as Lint results. The next implementation task is to introduce a controlled Lint validation step, first observing its findings before considering any automated cleanup.


### Follow-up — isolated Android Lint workflows added (2026-10-26)

Added manual-only Lint workflows so baseline diagnostics can be collected without changing or blocking the existing release/build workflows:

- GBSat: `.github/workflows/android-lint.yml`, commit `5d113be7c56901fc18538f1cf6285f735360cbc0`. Runs `:app:lintDebug` and `:gbframe:lintDebug`, with HTML/XML report artifacts.
- GBCast: `.github/workflows/android-lint.yml`, commit `f84a5d0e42116caf3e38a9f33eeb5cefe283efba`. Runs `:flycast:lintDebug` and `:gbframe:lintDebug`, with HTML/XML report artifacts.

Both workflows are triggered only via GitHub Actions Run workflow (`workflow_dispatch`) and do not alter the normal build/release path. They install the Android SDK packages declared by each app and upload reports even if Lint fails.

**Status:** the workflows have been committed, but no run has been started from this session; therefore there are no Lint findings or pass/fail result yet. The next step is to dispatch each workflow, inspect the report artifacts, triage baseline findings, and only then decide whether fixes are warranted.


### Follow-up — first Android Lint baseline collected (2026-10-26)

Both manual workflows executed and uploaded HTML/XML reports. The workflow jobs failed because Android Lint reports existing errors; this is a useful baseline, not a release-build regression. GBFrame itself produced warnings/hints only in both consumers, with no Lint errors in its reports.

- GBSat Lint run: https://github.com/mindspot7/GBSat/actions/runs/40356376343 — app module: **7 errors, 18 warnings, 9 hints**; GBFrame module: **0 errors, 23 warnings, 2 hints**. All 7 app errors are `RestrictedApi` reports for `ComponentActivity.dispatchKeyEvent` calls in `MainActivity.kt` (lines 94, 169, 173, 197). These calls are used to remap gamepad key events; review whether narrowly scoped lint suppression is justified or a supported dispatch strategy can replace the direct super calls. Do not change input behavior without controller regression tests.
- GBCast Lint run: https://github.com/mindspot7/GBCast/actions/runs/40356405485 — app module: **13 errors, 88 warnings**; GBFrame module: **0 errors, 22 warnings**. Errors include `MissingPermission` for microphone capture in `SipEmulator.java:35`, `GestureBackNavigation` in `BaseGLActivity.java:392`, `AppLinkUrlError` for a manifest intent filter without a host, and `RestrictedApi` reports for `dispatchKeyEvent` remapping in `LibraryActivity.kt` and `NativeGLActivity.java`. The microphone issue needs tracing through the permission/request flow before changing behavior; the manifest issue needs checking against the intended deep-link contract; the Back warning needs a predictive-Back compatibility review.

Lint report artifacts were uploaded by the runs (`gbsat-android-lint-reports` and `gbcast-android-lint-reports`) and were inspected in XML form to enumerate the error IDs. Many remaining warnings concern dependency updates, KTX suggestions, SDK checks, static context references, resource usage, and view accessibility. Do not batch-upgrade dependencies or delete resources from Lint suggestions alone; classify each finding and check runtime/manifest/JNI/reflection usage first.

**Next safe action:** address security/behavior-sensitive findings individually, starting with the GBCast microphone permission path and manifest intent-filter contract, then assess the dispatchKeyEvent findings. Keep the manual Lint workflows as diagnostic-only until the baseline is triaged; normal APK build workflows remain unchanged.


### Follow-up — Android Lint fixes validated (2026-10-26)

The follow-up pushes triggered the isolated Lint workflows and the normal GBSat APK build. All four runs completed successfully:

- GBSat Android Lint: https://github.com/mindspot7/GBSat/actions/runs/40358844654 — **0 errors**, app module: 18 warnings and 9 hints; GBFrame module: 23 warnings and 2 hints.
- GBCast Android Lint: https://github.com/mindspot7/GBCast/actions/runs/40358857898 — **0 errors**, app module: 88 warnings; GBFrame module: 22 warnings.
- GBSat release APK build: https://github.com/mindspot7/GBSat/actions/runs/40358780338 — succeeded, including the shared 16 KB APK compatibility validation.
- GBCast application-ID consistency check: https://github.com/mindspot7/GBCast/actions/runs/40358805172 — succeeded.

The initial Lint errors were addressed narrowly: gamepad key-event redispatch uses local suppressions; microphone setup remains behind the existing RECORD_AUDIO permission-result grant path; the legacy file/content URI filter is documented as not being a verified web App Link; and existing controller/hardware Back routing is retained. The GestureBackNavigation suppression is a temporary compatibility decision; predictive-Back behavior still needs dedicated testing. No controller behavior, manifest exported state, or permissions were silently changed to make Lint green.

Remaining warnings were reviewed by category rather than bulk-fixed. GBCast reports StaticFieldLeak warnings for process-level emulator/UI/storage references and ExportedReceiver for RetroAchievementsHostOverrideReceiver, which intentionally accepts broadcasts from external proxy tooling and writes the RetroAchievements host override. These need a separate lifecycle/security review before changing ownership or restricting the broadcast contract. UnusedResources warnings are not sufficient evidence to delete resources without checking theme/dynamic/JNI usage. Dependency and SDK update suggestions should be handled in a separate compatibility-tested update pass.

**Status:** targeted Lint errors resolved and the normal GBSat release build passed. The broad code review is not fully closed: next priorities are the exported receiver trust boundary, static context lifetime, predictive Back, and the remaining warning/resource inventory. GBFrame itself continues to report warnings/hints only and no Lint errors in both consumers.
