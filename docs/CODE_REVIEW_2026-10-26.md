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

### Follow-up — GBCast lifecycle/security triage and final Lint run (2026-10-26)

- GBCast Lint after the earlier Back-comment clarification completed successfully: https://github.com/mindspot7/GBCast/actions/runs/40359817189 (0 Lint errors).
- Added `GbcastGameUi.detach(Activity)` and called it from `NativeGLActivity.onDestroy()` to clear the static references to the game Activity and views, reset menu ownership, and avoid retaining an obsolete UI session. The ComposeView already uses `DisposeOnViewTreeLifecycleDestroyed`, so this is an explicit cleanup of the process-level references; it still needs device lifecycle testing.
- Hardened `RetroAchievementsHostOverrideReceiver` input: SET now accepts only absolute hierarchical HTTP(S) URLs with a non-empty host, no embedded user-info, and no control characters. This prevents line/control-character injection into the persisted `emu.cfg` value while preserving non-local proxy URLs. The receiver remains exported and does not authenticate the sender; another installed app can still change or clear the override. That trust-boundary choice remains an open security/product decision because restricting it may break RAOfflineProxy and similar tools.
- Final targeted Lint run for commit `dfbee0f7f8a0d7a3b436d76a21e1aeed0af019bb` succeeded: https://github.com/mindspot7/GBCast/actions/runs/40360701149. Reports: app module **0 errors, 89 warnings**; GBFrame module **0 errors, 22 warnings**. The app warning count differs from the earlier baseline by one; the static-analysis run confirms no errors, not that all warnings are resolved.
- Reviewed remaining `StaticFieldLeak` reports: `Emulator.context` is set to the application context and is intentional; `Storage` also stores the application context; `GbcastGameUi` contains process-level UI references that now have explicit teardown. Android Lint cannot prove runtime lifecycle correctness, so test repeated game launch/exit, rotation/configuration handling as supported, background/foreground, and opening/closing the quick menu on-device.
- `ExportedReceiver` is intentionally retained pending an explicit trust-boundary decision. `ObsoleteSdkInt` findings were not removed mechanically: SDK checks may be defensive or in code shared from upstream, and changing them needs compatibility review.
- No device test or release APK build has yet been run for these newest commits. The review remains open. Next: run the standard GBCast build/unit-test/16 KB validation on the current `android-port` head, then inspect receiver integration documentation/callers and perform lifecycle/device regression tests before deciding whether to require a sender permission or change exported status.


### Follow-up — GBCast standard build and unit tests passed (2026-10-26)

The standard Android workflow completed successfully on GBCast commit `560f52ecbd8db15dffd04840d809f6f50f5ab89c`:

- Run #40361771597: https://github.com/mindspot7/GBCast/actions/runs/40361771597
- The Gradle step ran `:flycast:testDebugUnitTest` and `:flycast:assembleRelease`; both completed successfully.
- The shared GBFrame 16 KB compatibility validator completed successfully against the packaged release APK.
- The APK artifact was uploaded successfully.
- The preceding Android Lint run #40360701149 remains green with 0 errors; the reports still contain 89 app warnings and 22 GBFrame warnings.

This closes the CI build/test/package gate for the current GBCast changes, but not the overall review: device lifecycle scenarios have not been rerun specifically against this newest APK, and the exported RetroAchievements override receiver remains an open trust-boundary decision. The receiver intentionally supports external proxy integrations; because it has no sender permission, any installed app able to send the matching broadcast can set or clear the configured host. URL validation blocks malformed/control-character values but does not authenticate the sender or establish that the destination is trusted. Do not mark this risk resolved solely because the build and Lint pass.

### Follow-up — build-trigger coverage gap confirmed

The GBCast standard Android workflow is triggered by manual dispatch or a push to `.github/workflows/run-build.trigger` on `android-port`; ordinary source changes do not automatically run the release build/unit tests/16 KB validator. The isolated Lint workflow has broader path filters, but it is not a substitute for the build gate. This appears to be an intentional, manually controlled build strategy, so no trigger behavior was changed automatically. For ongoing protection against source regressions, either retain an explicit documented manual build requirement or add source-path triggers after considering the native build cost and submodule update flow.


### Follow-up — proxy URL logging hardening (2026-10-26)

A focused security/privacy improvement was committed to GBCast `android-port`:

- Commit `9929728a7c54cf054287ca0614ed03cbc0a65b1e`: https://github.com/mindspot7/GBCast/commit/9929728a7c54cf054287ca0614ed03cbc0a65b1e
- `RetroAchievementsHostOverrideReceiver` no longer logs the full host override URL when a SET broadcast arrives or when `emu.cfg` is updated. The endpoint may include query parameters that should not be copied into Android logs.
- The log now records only that the override was set/cleared; URL validation and the external broadcast contract are unchanged.
- Validation was triggered: Android build run #40363852313 (queued at report time) and Android Lint run #40363821333 (in progress at report time). Do not mark this change CI-validated until both results are checked.

This reduces accidental disclosure of proxy URL query values in logs. It does **not** authenticate the sender or resolve the existing exported-receiver trust-boundary decision.


### Follow-up — GBCast privacy hardening CI validated (2026-10-26)

The two workflows triggered after commit `9929728a7c54cf054287ca0614ed03cbc0a65b1e` completed successfully:

- Android build run #40363852313: https://github.com/mindspot7/GBCast/actions/runs/40363852313 — Gradle build/tests, release APK packaging, shared 16 KB compatibility validator, and artifact upload all succeeded.
- Android Lint run #40363821333: https://github.com/mindspot7/GBCast/actions/runs/40363821333 — Lint and report-artifact upload succeeded.

The latest CI gate therefore validates the URL-log redaction change at build/test/package and Lint workflow level. It does not authenticate broadcast senders, prove runtime lifecycle correctness, or replace on-device testing.

The receiver manifest still intentionally declares `android:exported="true"` and has no sender permission. Repository code search did not find a documented in-repository RAOfflineProxy implementation or additional caller, so compatibility with external proxy tooling cannot be inferred from this repo alone. Do not add a signature permission or disable export without confirming that the external tool can use it; that would otherwise break the integration. The trust-boundary decision remains open and should be explicit in integration documentation/release notes. URL validation and redacted logging reduce input-injection and accidental log-disclosure risks, but they do not make the caller trusted.

**Current status:** latest GBCast CI build/test/package and Lint workflows passed. The review remains open pending the external-integration trust decision and on-device lifecycle regression tests for the UI-reference cleanup.


### Follow-up — external RAOfflineProxy compatibility check

Checked the public RAOfflineProxy source rather than assuming the external broadcast contract:

- RAOfflineProxy's current `Emulator.Flycast` configuration lists only `com.flycast.emulator` as a package candidate and expects the receiver class `com.flycast.emulator.RetroAchievementsHostOverrideReceiver`: https://github.com/misantronic/RAOfflineProxy/blob/main/app/src/main/java/com/raofflineproxy/ui/Emulator.kt
- Its broadcast patcher builds the action by concatenating the detected target package name with `.action.SET_RETROACHIEVEMENTS_HOST_OVERRIDE` / `.action.CLEAR_RETROACHIEVEMENTS_HOST_OVERRIDE`, and targets that package/component explicitly: https://github.com/misantronic/RAOfflineProxy/blob/main/app/src/main/java/com/raofflineproxy/ui/BroadcastCfgPatcher.kt
- GBCast's Gradle namespace and application ID are `app.gbcast.emulator`, while its receiver currently declares the legacy `com.flycast.emulator.action.*` actions.

**Confirmed integration gap:** current upstream RAOfflineProxy does not list GBCast's new application ID, so it will not discover/target this GBCast installation through its current Flycast entry. The legacy action names in GBCast's manifest do not solve that package-discovery mismatch. A receiver-only change in GBCast cannot fully fix this; RAOfflineProxy needs an explicit supported package/receiver mapping (or another supported manual setup path). The external repo's main branch is not writable from this integration, so no external changes were made.

Security decision: the receiver remains exported because the integration contract requires cross-app broadcasts. The safer compatible long-term design is for RAOfflineProxy and GBCast to agree on an explicit permission/authentication mechanism and for RAOfflineProxy to target `app.gbcast.emulator`; simply adding a custom signature permission on GBCast alone would block RAOfflineProxy unless both apps are signed compatibly and declare/use that permission. Do not mark the integration fixed until the external package mapping and end-to-end SET/CLEAR flow are verified.


### Follow-up — receiver trust boundary needs an explicit policy

A deeper review of `RetroAchievementsHostOverrideReceiver` found a residual security risk beyond unauthenticated SET/CLEAR broadcasts: `isValidHostUrl` accepts any absolute HTTP(S) URL with a non-empty host and no user-info/control characters. This is useful input hygiene, but it does not constrain the destination to a trusted/local proxy. A malicious installed app that can send the exported broadcast could attempt to redirect RetroAchievements traffic to a third-party host. The practical impact depends on the emulator's request/authentication behavior, which has not been verified here; do not claim credential exposure as confirmed without a runtime test.

Recommended follow-up is to decide whether arbitrary remote/custom proxy URLs are a supported use case. If only local proxy integrations are required, restrict allowed destinations to loopback and validate ports/URI authority rigorously. If arbitrary proxy URLs are required, document the trust boundary and adopt an interoperable sender-authentication mechanism with the external client. A signature-level permission on GBCast alone would block the existing RAOfflineProxy unless both apps can share compatible signing and permission declarations.

At the time of the initial attempt, GitHub rejected issue creation because Issues were disabled (HTTP 410). Issues have since been enabled, and two tracking issues were created in GBCast: [#1 — define the trust policy for the exported receiver](https://github.com/mindspot7/GBCast/issues/1) and [#2 — RAOfflineProxy compatibility after the application-ID change](https://github.com/mindspot7/GBCast/issues/2).

**Action still required:** coordinate support for the new package ID `app.gbcast.emulator` with RAOfflineProxy upstream, then test SET/CLEAR against the installed GBCast build. The GBCast repository integration currently has read-only access to RAOfflineProxy, so no upstream changes were made.


### Follow-up — GBCast issue tracking enabled (2026-10-26)

After the repository owner enabled Issues, created two open tracking issues:

- [GBCast #1 — Security: define trust policy for exported RetroAchievements host override receiver](https://github.com/mindspot7/GBCast/issues/1). Tracks the decision between a loopback-only policy and interoperable sender authentication, plus validation and runtime tests. No concrete credential exposure is claimed.
- [GBCast #2 — Integration: make GBCast discoverable by RAOfflineProxy after application ID change](https://github.com/mindspot7/GBCast/issues/2). Tracks the `app.gbcast.emulator` package/component mismatch with upstream RAOfflineProxy and end-to-end SET/CLEAR verification.

Both issues are open. Creating them does not resolve either finding; receiver policy, upstream integration, and device verification remain pending.


### Follow-up — isolated proxy URL validation and JVM tests (2026-10-26)

GBCast `android-port` now separates URL syntax validation from the Android broadcast receiver:

- `RetroAchievementsHostUrlValidator` uses pure Java `java.net.URI`, allowing the URL rules to be exercised by ordinary JVM unit tests without mocking Android's `Uri`.
- The receiver delegates SET input validation to this helper; full proxy URLs remain redacted from logs.
- New JUnit coverage checks valid HTTP/HTTPS URLs, missing/unsupported schemes and hosts, user-info, invalid/out-of-range ports, control characters, whitespace, and malformed URIs.
- Commits: receiver refactor `d6b720ab0f7a3267b6b481087fda3e8f4ff5bff1`; validator `c99e0dbe9536092b0932cc92fcadf0323f245627`; tests `d65683e8dcbcd1209a4da600adbc1deeeda64ed9`; build trigger `9902a0ae5933cf000f222a9cb26211d32c4f38d2`.

Validation is running:
- Android build/test/package workflow: https://github.com/mindspot7/GBCast/actions/runs/40366860986
- Android Lint workflow for the changed receiver: https://github.com/mindspot7/GBCast/actions/runs/40366810687
- Android Lint workflow for the new validator: https://github.com/mindspot7/GBCast/actions/runs/40366835381

**Important scope limit:** these tests validate URL syntax only. They do not decide whether remote proxy destinations are trusted, authenticate the sender, or prove that the emulator's RetroAchievements requests are safe when redirected. The trust-policy issue remains open at [GBCast #1](https://github.com/mindspot7/GBCast/issues/1); package discovery by upstream RAOfflineProxy remains open at [GBCast #2](https://github.com/mindspot7/GBCast/issues/2). Do not close either issue solely because the new tests/build pass.

### Follow-up — CI passed and config-write consistency fix (2026-10-26)

The proxy URL validator and its JVM tests passed the GitHub Actions Android build/test/package workflow (run [#40366860986](https://github.com/mindspot7/GBCast/actions/runs/40366860986)) and Android Lint for the test commit (run [#40366835381](https://github.com/mindspot7/GBCast/actions/runs/40366835381)). An older overlapping Lint run was cancelled when a newer run superseded it; it was not a test failure.

A further receiver review found that `nativeSetHostOverride()` was called even after writing `emu.cfg` failed, allowing in-memory and persistent configuration to diverge. Updated `RetroAchievementsHostOverrideReceiver` to return after an `IOException`, so the in-memory override is not applied unless the persistent update succeeds.

- Fix commit: `9e7e5c209639cbedd8ed385b9a42e80fe6fb533c`
- The CI results above predate this final consistency fix; validation of the fix itself is still outstanding.
- Security trust policy and RAOfflineProxy package compatibility remain open and are not resolved by this change.
### Validation result — config-write consistency fix (2026-10-26)

The follow-up validation for commit `9e7e5c209639cbedd8ed385b9a42e80fe6fb533c` completed successfully:

- Android Lint: [run #40368339898](https://github.com/mindspot7/GBCast/actions/runs/40368339898) — success.
- Android build, `:flycast:testDebugUnitTest`, release APK packaging, and shared 16 KB APK validation: [run #40368530356](https://github.com/mindspot7/GBCast/actions/runs/40368530356) — success.

This verifies CI on the config-write consistency fix. It does not replace an installed-device SET/CLEAR test and does not resolve the exported receiver's trust policy or RAOfflineProxy's package mapping.

### Follow-up — offline proxy mode restricted to device loopback (2026-10-26)

The user clarified that local proxies are used by the offline mode, while the normal/online mode is different. GBCast's host override receiver is the integration point used by RAOfflineProxy to redirect RetroAchievements traffic to its local proxy; the normal online flow does not need this override. The validator now accepts only HTTP(S) URLs whose host is exactly localhost, 127.0.0.1, or IPv6 loopback ::1. Remote hosts, LAN IPs, the Android Emulator host alias 10.0.2.2, and other loopback-range addresses such as 127.0.0.2 are rejected. Existing local paths/query parameters remain permitted.

Commits on GBCast android-port:
- Validator + strict loopback policy: current file update based on previous validator commit.
- Tests now cover accepted IPv4/IPv6/localhost endpoints and rejected public, LAN, emulator-host, and non-canonical loopback destinations.
- Receiver rejection log updated to describe the actual local-only rule.

This policy is scoped to the offline-proxy host override, not the normal/online RetroAchievements flow. It is intentionally device-loopback-only: if the offline proxy runs on a separate LAN device or on the development host via 10.0.2.2, that is not supported by this policy.

Remaining checks: run the standard GBCast build/unit tests/release packaging/16 KB validation and Android Lint on the resulting branch head, then verify SET/CLEAR and normal online behavior on-device. The exported receiver still does not authenticate the broadcast sender, and RAOfflineProxy's package/component mismatch remains a separate open integration issue.

### Validation result — loopback-only offline proxy policy (2026-10-26)

The latest GBCast CI runs for the local-only offline proxy override completed successfully:
- Android build/test/package workflow on commit `2ac7f2bfdb9570396ff3f88bd5d5cfb479eaaf4e`: [run #40370599373](https://github.com/mindspot7/GBCast/actions/runs/40370599373) — success.
- Android Lint on commit `2f12d83de960e5c7a8da04cd13515ada7f1afe9b`: [run #40370532437](https://github.com/mindspot7/GBCast/actions/runs/40370532437) — success.

RAOfflineProxy's current Flycast configuration was re-read. It only lists `com.flycast.emulator` as the Flycast package candidate and targets `com.flycast.emulator.RetroAchievementsHostOverrideReceiver`. GBCast retains that receiver class name but its installed application ID is `app.gbcast.emulator`, so RAOfflineProxy will not automatically discover GBCast until its Flycast package-candidate mapping is updated upstream. A request to document this compatibility gap on RAOfflineProxy's integration-tracking issue could not be posted because the GitHub integration returned HTTP 403 (resource not accessible); no upstream file or issue was changed.

Still unverified: on-device SET/CLEAR behavior, actual offline achievement queueing and upload after reconnect, and confirmation that ordinary online RetroAchievements works unchanged. The exported receiver sender-authentication concern also remains open. CI success confirms build/lint/unit-test gates only, not the full device integration.


### Follow-up — SET/CLEAR persistence tests and sender-authentication review (2026-10-26)

Added Android instrumentation tests on GBCast branch `android-port` to send explicit SET and CLEAR broadcasts to the exported receiver and verify `emu.cfg` persistence. Coverage includes:
- SET replaces the previous `[achievements] HostUrl` while preserving other settings and sections.
- CLEAR removes `HostUrl` while preserving unrelated settings.
- A remote/non-loopback SET is rejected without overwriting the persisted local host.
- The Android CI workflow now compiles the Android instrumentation test source in addition to JVM unit tests and APK packaging.

Commits:
- Instrumentation tests: `09ebcf8a4337251e81de12486800e90d5d6e62b1`
- CI compile-task and build trigger: `66d31857316e88b4087a56d92a314f669b454032`

The Android build workflow #65 and Android Lint #15 were triggered and are pending at the time of this report update. These tests are not yet a device-level pass until CI compilation succeeds and the instrumentation suite is executed on an Android device/emulator.

#### Sender authentication finding remains open

Confirmed: loopback URL validation restricts the *destination*, not the *sender*. Because the receiver is exported and has no permission/caller verification, another installed app can still attempt SET or CLEAR. This is a separate trust-boundary issue from URL validation.

A GBCast-only Android `signature` permission would block unauthorized callers but is not a safe drop-in integration fix: RAOfflineProxy is a separately distributed app, and shared signing-certificate compatibility has not been established. A normal/dangerous permission or explicit component targeting alone is not strong authentication. The security issue remains open pending a coordinated protocol with RAOfflineProxy (e.g. shared-signature permission if co-signing is feasible, or another caller-verification/explicit-authorization design that supports the app's Android API range).

Tracked in [GBCast security issue #1](https://github.com/mindspot7/GBCast/issues/1). Do not treat sender authentication as fixed. Full device verification of SET/CLEAR, offline queue/sync after reconnect, and ordinary online RetroAchievements behavior is still outstanding.


### CI follow-up — execute receiver instrumentation tests (2026-10-26)

The earlier Android workflow #65 and Android Lint #15 both completed successfully:
- [Android build/test/package #65](https://github.com/mindspot7/GBCast/actions/runs/40373074726) — success; this compiled `:flycast:compileDebugAndroidTestJavaWithJavac` but did not execute instrumentation tests.
- [Android Lint #15](https://github.com/mindspot7/GBCast/actions/runs/40373000508) — success.

To close that gap, the Android workflow now starts an API 35 Google APIs x86_64 emulator and runs `:flycast:connectedDebugAndroidTest`, which installs the app/test APK and executes SET/CLEAR persistence tests. The workflow was retriggered by updating `.github/workflows/run-build.trigger`; execution result is pending at this report update.

Commits:
- Emulator execution step: `1e112d1fd2f048b0bae577b737e23ebd632a5433`
- Workflow trigger: `b2e45f0f717f90e69776fc3e388b43262b781d42`

The sender-authentication issue remains intentionally unresolved: a loopback destination check is not sender authentication, and adding a signature permission without confirming a compatible shared signing identity for RAOfflineProxy risks breaking integration. Keep GBCast issue #1 open until an interoperable sender-trust design is established and tested.
