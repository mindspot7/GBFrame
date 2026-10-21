# Lessons learned

What building GBSat (Sega Saturn, Ymir core) taught us, to apply from day one in every app of the family. Add a
line here whenever a new app teaches something that would have saved time.

## Measure before optimizing

- Profile on the slowest device (Unisoc T618 / Mali-G52) before changing anything. A `profileable` manifest flag plus
  a "Profiling build" workflow that keeps native symbols (`keepDebugSymbols`) and a one-click `simpleperf` script
  (`tools/profile.bat`) gives per-thread and per-function numbers in minutes.
- Give every native thread a name of **15 characters or less**: Linux rejects longer names, the thread keeps the app's
  name, and both profiling and per-thread CPU placement silently stop working.
- `cpu-clock` profiles don't show time spent waiting (GPU fences, sleeps). For GPU renderers, add counters instead:
  batches per frame, mid-frame GPU syncs, time waiting for the GPU, delivered fps.
- Separate builds (profiling, PGO training) must use the same version code as the latest normal APK, or Android
  refuses to install them over it ("invalid package").

## CPU

- Run sound on its own thread and pin the emulation thread alone on the fastest core (rank cores by
  `cpu_capacity`); move threads that inherited its affinity (driver threads, recreated render threads) off it.
- Keep one Android performance hint session, on the emulation thread only; recreating it leaves lasting slowdowns.
- Waiting threads must not spin with `sched_yield` (a syscall per loop): spin briefly with a CPU pause instruction,
  then block (futex / `atomic::wait`).
- Profile-guided optimization (`-fprofile-generate` training build on the device, merged `.profdata` committed to the
  repo, `-fprofile-use` in normal builds) gave ~12% on the interpreter thread at no code cost. Merge the raw profile
  with an `llvm-profdata` of the same LLVM version as the NDK clang.
- A dynarec only pays off when the CPU interpreter thread is the bottleneck at full speed; check the profile first.

## GPU renderers (Vulkan on Mali)

- Mobile GPUs hate many small dispatches: per-scanline register changes (raster effects) must go to the GPU as
  per-line tables, not as one batch per line (DonPachi: 240 batches → ~6 per frame, 30 → 60 fps).
- Read frames back from **cached** host memory (`HOST_CACHED`, invalidate if not coherent) and read it once:
  uncached reads are very slow on Mali.
- Keep the software renderer: some games stay faster there on weak GPUs; the GPU path wins on Adreno-class devices.

## Frame pacing

- Automatic frame skip helps only when rendering is the bottleneck; when the CPU emulation is, skipping just makes
  the picture choppy, so back off (stop skipping for a while) when skipping doesn't catch up.
- Fast forward should render ~60 frames per real second and skip the rest.
- Present frames zero-copy through `AHardwareBuffer`.

## Controller and menus

- Every dialog/menu must work with the controller from the first press: draw dialogs in the app window, focus the
  first item, never let the game `SurfaceView` take key focus, let Android map hat-axis D-pads to D-pad keys while a
  menu is open, and handle global buttons (Select, B in dialogs) in the Activity.
- With targetSdk 36 (predictive back), map the controller's B to `onBackPressedDispatcher.onBackPressed()`.

## Storage, saves and Play

- No `MANAGE_EXTERNAL_STORAGE`: read games through SAF (folder picker + persisted tree URI); path-based cores keep
  working with `/proc/self/fd/N` symlinks in a staging folder.
- Export (saves + save states only) / Import, game-named save folders.
- Give games a save (backup memory) cartridge when they don't need a RAM cartridge: some games ask for one.
- Google Play: targetSdk 36, 16 KB pages, AAB in the build artifacts only, edge-to-edge, privacy policy, no console
  maker trademarks in the name or icon.

## CI

- Build only on GitHub Actions; watch every run and fix warnings too (deprecated actions included).
- Shared submodules (like this kit) must be public, or the app's CI can't clone them.
