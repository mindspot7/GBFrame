# Build and validation standard

Keep one active GitHub Actions workflow per repository, named `Build <project>`
and stored in `.github/workflows/build.yml`: GBFrame builds its release
library; each consumer builds its signed release APK. Consumers must use the tested,
pinned GBFrame commit. Validate GBFrame first, then build the consumers.

Functional tests, unit tests, instrumentation/device tests, native renderer/CPU tests,
Android Lint, screenshot generation and profiling/PGO validation run locally. Keep
those test sources and local tools. Do not add separate online test, lint, screenshot,
profiling, PGO or app-ID workflows, or make package generation depend on them.

GitHub builds remain explicit: manual dispatch or a dedicated build-trigger file.
Ordinary synchronization must not build APKs. Cloud workflows compile release
artifacts, sign, validate the package (including Android 16 KB compatibility for
native APKs), and upload it. App Bundles are limited to the existing official-release
rule or an explicit request. Keep dependency/compiler caches; do not compile debug
or x86 emulator builds, download Android emulator images, or boot virtual devices
just to package an ARM64 release.

Before dispatch, run the relevant checks on the local machine and attached Android
device. Select meaningful checks for the change; do not repeat the entire suite for
unchanged code. Preserve user data and signatures when installing test updates.
Only report a cloud build as successful after its run completes successfully and
its expected artifacts are available. GBFrame's library-only workflow currently
checks compilation without uploading an artifact; consumers upload their APKs.
