# GB family CI contract: Android 16 KB page compatibility

Status: required baseline for every Android app in the GB family.

## Scope and ownership

GBFrame owns this shared policy and its documentation. Each consumer repository (including GBSat and GBCast) must enforce the checks against its **final packaged APK** in its own CI workflow. A successful GBFrame library build cannot prove that an app's complete native dependency set is compatible.

## Required CI gates

1. Build the APK and run the app's configured unit tests.
2. Run Android `zipalign -c -P 16 -v 4` against the exact APK that will be uploaded as the CI artifact.
3. Extract every `lib/arm64-v8a/*.so` from that APK and inspect ELF program headers with NDK `llvm-objdump -p`.
4. Fail the job if any inspected ELF `LOAD` segment reports `2**12` alignment, if a required inspection tool is missing, if the APK has no expected ARM64 native libraries, or if no LOAD alignment can be parsed.
5. Upload the same APK that passed the checks. Do not rebuild or replace it after validation.

These gates must be blocking failures, not warnings. If an app intentionally has no ARM64 native libraries, document and implement an explicit app-specific exception rather than allowing an accidental empty glob to pass.

## Scope and limits

- The ZIP alignment check validates packaging alignment; it does not prove ELF segment alignment.
- The ELF check covers the ARM64 shared libraries actually packaged in the APK. If an app supports other ABIs, add equivalent inspection for each shipped ABI.
- Passing static alignment checks is evidence for 16 KB page compatibility, not a substitute for install/launch/gameplay tests on a device or emulator configured with 16 KB pages.
- Keep app-specific build steps (NDK/CMake/DXC, signing, emulator core and cache setup) in each consumer workflow. Share the validation contract, not assumptions about each core's build system.

## Rollout checklist

- [x] GBCast: ZIP check and mandatory ARM64 ELF gate.
- [x] GBSat: ZIP check and mandatory ARM64 ELF gate.
- [ ] Audit remaining GB family Android apps and apply the same contract.
- [ ] Where feasible, add a scheduled/device test on a 16 KB-page Android environment.
