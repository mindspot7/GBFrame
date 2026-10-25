# Lessons learned

What building GBSat (Sega Saturn, Ymir core) taught us, to apply from day one in every app of the family. Add a
line here whenever a new app teaches something that would have saved time.

## Measure before optimizing

- Profile on the slowest device (Unisoc T618 / Mali-G52) before changing anything. A `profileable` manifest flag plus
  a "Profiling build" workflow that keeps native symbols (`keepDebugSymbols`) and a one-click `simpleperf` option in
  the test script (`tools/emu-test.bat`, option 3) gives per-thread and per-function numbers in minutes.
- For hot spots inside one big function, resolve the profile's addresses to source lines: take `lib<app>.so` from the
  profiling APK and run `llvm-addr2line -f -i -C` on the "by address" section (inlined frames included).
- Check *why* a hot path is entered before optimizing it: the SH-2 chained interpreter returned to its caller ~12% of
  the time; keeping it running through loops gave nothing, because the real reason was the 32-cycle time slices of the
  two CPUs. Skipping the slow first instruction of each slice did help.
- The performance log times components on a sample of frames (1 in 7). Those frames must run the same code as the
  others: a fast path disabled on measured frames made the log time the old path and hid the gain.
- Compare runs of the same scene only: a 30 s sample varies by a few percent between runs. Bucket the seconds by load
  (e.g. VDP1 commands per frame) and compare buckets, and prefer A/B switches inside one run (`diag_props.hpp`,
  `debug.<app>.<name>` set by the test script) over builds compared across time.
- A faster component often just moves the wait: a faster SH-2 made the emulation wait for the VDP2 thread, then for
  the sound thread. Log every wait per helper thread and per wait site (`wait_vdp2_frame_ms`, `wait_vdp2_copy_ms`).
- Kernel time in a cpu-clock profile concentrated on one address (IRQ re-enable after scheduler work) is not proof of
  costly thread wake-ups: batching the render threads' events cut wake-ups a lot and changed nothing measurable.
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
- When a screen leaves composition, clear any focus flags stored outside Compose state. A stale `hasFocus=true` can make focus restoration skip the requester and leave selection behind an overlay. Do not attach one `FocusRequester` to multiple controls when only one should receive initial focus.

## Storage, saves and Play

- Auto load should continue from the game's latest save state in any slot, not only the auto save; write the auto
  save also when the app goes to the background (Android may close it there, and the in-game Exit never runs).
- Remember UI state such as the library layout and selected game between sessions; do not persist a removed library mode.

- No `MANAGE_EXTERNAL_STORAGE`: read games through SAF (folder picker + persisted tree URI); path-based cores keep
  working with `/proc/self/fd/N` symlinks in a staging folder.
- Reopening a `/proc/self/fd/N` link *by path* (fopen/ifstream/mmap of the path) is refused on some devices (Anbernic
  RG DS: `stat` works, `fopen` fails, libchdr says "file not found"). Copying the file to the cache worked around it but
  costs minutes and gigabytes. The core's loaders therefore read the staging symlinks through the descriptor itself
  (`readlink` -> `/proc/self/fd/N`, in gbsat-core `ymir/media/proc_fd.hpp`): CHD via `chd_open_core_file_callbacks` + `pread`,
  ISO/CUE/MDS/CCD text and headers via `InputFile` (an istream over the descriptor), file mappings via `mio` on the
  descriptor. The copy of a CHD/ISO stays only as a fallback (`SafGames.open(path, stageCopy = true)`; the app retries with it
  when loading fails). Not covered: the wide-char MDS filename read.
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
- Profile (simpleperf, test script option 3 with the profiling build) before a big CPU optimization: on the T618 the SH-2 interpreter turned out to have spare time at 60 fps (emulation thread 55-70% of a core), so a decoded-instruction cache would not have changed the frame rate, while the sound thread's polling did.
- A threaded renderer that reads the other chip's display buffer (Saturn VDP2 reading the VDP1 framebuffer) should take its own copy at the start of each frame: the producer's erase/swap then waits only for the copy, not for the whole frame (VF2 drawn frames 21-24 ms -> 13 ms).

## Interpreters

- A threaded ("computed goto") interpreter, where each instruction jumps straight to the next handler, cut the SH-2
  time by ~9% on the T618 (one shared dispatch point was mispredicted most of the time). Keep the plain interpreter
  too, and a CI test that runs random programs through both and compares every register and memory write.

## Threaded renderers and presentation

- A render thread that draws into the only framebuffer forces the emulation to wait at the first line of the next
  frame until the previous one is finished. Two framebuffers that alternate (progressive screens, and interlaced ones
  when each drawn frame fills both fields) removed it: Burning Rangers on the T618 went from 58.2 to 59.8 fps with no
  second below 58. Frames the frontend skips must not force the delivery of the pending frame either.
- GPU usage for the overlay: on many Android devices the driver's sysfs load can't be read by apps (SELinux). Measure
  the app's own GPU time instead (`GL_EXT_disjoint_timer_query` around the presenter's draws, Vulkan timestamps at the
  start and end of each command buffer, read after the fence), never waiting for a result.
- RetroArch's multi-pass CRT shaders (crt-royale: 8-12 passes with mask textures; GTU: 3 passes) fit a Mali-G52 as
  single-pass approximations that keep their look (linear light, brightness-dependent gaussian beams, aperture grille
  mask with brightness compensation, halation; GTU's band-limited signal and integrated beams). Validate GLSL with
  `glslangValidator` and preview with a numpy mirror of the shader on frames captured on the device before shipping.
- Colour corrections such as the bsnes gamma ramp are a separate option applied to the source colours, combinable with
  any shader (RetroArch users stack them).
- FXAA gains little on these consoles: pixel art and HUD text get blurred, 3D edges improve modestly at native
  resolution. Bilinear is not anti-aliasing (it softens the steps but keeps their shape). Decide with previews.

## Loading

- Skip Engine (`auto_skip`): maximum speed only while a load, transition or save wait is detected (disc activity with a
  still or black, silent picture; silent black frames; bulk backup memory access), with hysteresis. It is limited by
  the device's speed, so it complements a faster disc read speed instead of replacing it.
- Faster disc reads can desync videos and streamed music: turn the speedup off while the game streams (disc activity
  with a moving picture and sound), like DuckStation does for XA/CD audio and video decoding. Then a higher default
  (8x) is safe. Apply a speed change to a read already in progress.

## Interface

- A lazy list's scroll state created while the data is still loading stays on the first item: recreate it once the
  data arrives (keyed on "list empty"), or the selected item is off screen when it should take the focus.
- Programmatic focus right after a screen appears must retry until the requester is attached and the focus is really
  in the list; a single try after a fixed delay often missed, and the first D-pad press then picked another item.
- Overlay colours follow common emulators: FPS and speed green from 95% of full speed (DuckStation and PCSX2/AetherSX2
  also treat below 95% as slow), yellow from 80%, red below.
