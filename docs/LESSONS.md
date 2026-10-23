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

- Pin the emulation thread alone on the fastest core (rank cores by `cpu_capacity`); move threads that inherited its
  affinity (driver threads, recreated render threads) off it.
- A thread per chip is not free: with only 2 fast cores (T618), a threaded sound chip took the core the background
  renderer needed (VF2: 30 fps threaded vs 50-60 fps unthreaded). But other games (Burning Rangers, NiGHTS) need it: tune it per game
  at runtime (Auto mode: try the other mode when slow, keep the faster, remember per game).
- CPU profiles don't show waits: log per frame how long the emulation thread waits for each helper thread (sound,
  VDP1, VDP2) together with the active settings; it found what profiles could not.
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

- Auto load should continue from the game's latest save state in any slot, not only the auto save; write the auto
  save also when the app goes to the background (Android may close it there, and the in-game Exit never runs).
- Remember UI state such as the library layout between sessions (`LibraryView`).

- No `MANAGE_EXTERNAL_STORAGE`: read games through SAF (folder picker + persisted tree URI); path-based cores keep
  working with `/proc/self/fd/N` symlinks in a staging folder.
- Export (saves + save states only) / Import, game-named save folders.
- Give games a save (backup memory) cartridge when they don't need a RAM cartridge: some games ask for one.
- Google Play: targetSdk 36, 16 KB pages, AAB in the build artifacts only, edge-to-edge, privacy policy, no console
  maker trademarks in the name or icon.

## CI

- Build only on GitHub Actions; watch every run and fix warnings too (deprecated actions included).
- Shared submodules (like this kit) must be public, or the app's CI can't clone them.
- Counting fast cores is not enough to classify a device: chips with equal little cores (RK3566, 4x A55) look like
  "4 fast cores", and a fast CPU can come with a GPU too weak for GPU emulation (T820 / Mali-G57). `DeviceTier` reads
  the core type (/proc/cpuinfo) and the GPU name (GL_RENDERER) as well. Old big cores (A73, e.g. Snapdragon 662) are
  slower per core than a T618; Mali strength depends on the core count in the name ("Mali-G615 MC6").
- After a touch, Android consumes the first D-pad press to leave touch mode (before the Activity sees it): with a
  controller that feels like a dead first press. Route D-pad keys in touch mode from `dispatchKeyEventPreIme`
  (`setControllerContent`).
- Compose will not focus clickable items while Android is in touch mode, and a key consumed before the view tree (as `setControllerContent` does) never leaves it: call `requestFocusFromTouch()` first, or the programmatic focus after a tap silently fails.
- Before a JIT, measure idle loops: on Saturn games the SH-2s spent 60-100% of their interpreted cycles in loops of at most 5 instructions that only poll memory. Skipping such a loop to the end of the CPU's time slice is exact (nothing else runs during the slice) and much cheaper than a recompiler; exclude memory with read side effects (FIFO data ports) and loops that carry a register between iterations.
- Threads that wait for work all the time (a sound chip thread between sample ticks) must not use `std::atomic::wait` on Android: libc++ polls with `clock_gettime` and `sched_yield` for up to ~64 us before sleeping, which was 12-36% of a core on the T618 (simpleperf). Spin briefly, then a futex wait, with the waker only calling FUTEX_WAKE when the thread announced it is sleeping (GBSat SCSP).
- Profile (simpleperf, `profile.bat` with the profiling build) before a big CPU optimization: on the T618 the SH-2 interpreter turned out to have spare time at 60 fps (emulation thread 55-70% of a core), so a decoded-instruction cache would not have changed the frame rate, while the sound thread's polling did.
- A threaded renderer that reads the other chip's display buffer (Saturn VDP2 reading the VDP1 framebuffer) should take its own copy at the start of each frame: the producer's erase/swap then waits only for the copy, not for the whole frame (VF2 drawn frames 21-24 ms -> 13 ms).
