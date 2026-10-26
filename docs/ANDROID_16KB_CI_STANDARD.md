# GB family CI contract: Android 16 KB page compatibility

Status: required baseline for every Android app in the GB family.

## Scope and ownership

GBFrame owns this shared policy and its documentation. Each consumer repository (including GBSat and GBCast) must enforce the checks against its **final packaged APK** in its own CI workflow. A successful GBFrame library build cannot prove that an app's complete native dependency set is compatible.

## Shared validator

Use `tools/check-android-16kb.sh <final-apk-path>` from this repository. Consumer workflows should pin the download to an immutable GBFrame commit (or update their GBFrame submodule pointer) rather than follow a floating branch. The script performs both ZIP alignment and ELF `LOAD` alignment checks across all packaged ABIs and exits nonzero on missing tools, missing native libraries, unreadable alignment data, or a 4 KB `LOAD` alignment.

## Required CI gates

1. Build the APK and run the app's configured unit tests.
2. Run Android `zipalign -c -P 16 -v 4` against the exact APK that will be uploaded as the CI artifact.
3. Extract every packaged native library (`lib/*/*.so`) from that APK and inspect ELF program headers with NDK `llvm-objdump -p`, across all shipped ABIs.
4. Fail the job if any inspected ELF `LOAD` segment reports `2**12` alignment, if a required inspection tool is missing, if the APK has no native libraries, or if no LOAD alignment can be parsed.
5. Upload the same APK that passed the checks. Do not rebuild or replace it after validation.

These gates must be blocking failures, not warnings. If an app intentionally ships no native shared libraries, document and implement an explicit app-specific exception rather than allowing an accidental empty glob to pass.

## Scope and limits

- The ZIP alignment check validates packaging alignment; it does not prove ELF segment alignment.
- The shared validator inspects every native shared library actually packaged in the APK, across all ABIs.
- Passing static alignment checks is evidence for 16 KB page compatibility, not a substitute for install/launch/gameplay tests on a device or emulator configured with 16 KB pages.
- Keep app-specific build steps (NDK/CMake/DXC, signing, emulator core and cache setup) in each consumer workflow. Share the validation contract, not assumptions about each core's build system.

## Rollout checklist

- [x] GBCast: ZIP check and mandatory all-ABI ELF gate; workflow now invokes the pinned shared validator.
- [x] GBSat: ZIP check and mandatory all-ABI ELF gate; workflow now invokes the pinned shared validator.
- [ ] Audit remaining GB family Android apps and apply the same contract.
- [ ] Where feasible, add a scheduled/device test on a 16 KB-page Android environment.
