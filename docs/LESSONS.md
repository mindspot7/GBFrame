# Lessons learned

- Treat widescreen and frame-rate patches as distinct shared kit capabilities in Video and the in-game Performance menu. Prefer the core's native hack, then a curated game-specific code patch, then a matching database code; do not stack sources. Both default on and are suppressed in RetroAchievements Hardcore; reapply the effective patch set when the toggle, display mode or Hardcore state changes.

Record discoveries from GBFrame and each consumer here so the next emulator can start from measured behavior instead
of repeating the same investigation. Add a line whenever an app teaches something that would have saved time.

## Lessons by project

- **GBSat / Ymir (Sega Saturn):** renderer changes must be evaluated separately from CPU bottlenecks. On the RK3568,
  Vulkan presentation overhead and Saturn CPU limits are distinct; renderer experiments did not justify changing the
  proven OpenGL CPU path. Keep tested per-game CPU recommendations in GBFrame profiles and document the device/game
  scope of every exception.
- **GBCast / Flycast (Dreamcast):** keep shaders and picture scaling filters in the GBFrame presenter so OpenGL and
  Vulkan produce the same output. The Vulkan path can render off-screen and copy into GBFrame's AHardwareBuffer ring;
  submit a slot only after its Vulkan fence signals, and preserve CPU readback when import/copy support is unavailable.
  The Vulkan-to-AHardwareBuffer copy is still a GPU copy; it avoids the GPU-to-CPU readback and keeps effects in one
  implementation. Validate format, ownership transitions, synchronization, resize, and fallback on device before
  treating this path as production-ready.
- **GBFrame / shared Android behavior:** intent launch requests from frontends may contain either the ROM filename or
  the library's displayed title. Resolve exact and case-insensitive filenames first, then titles, so title-based
  requests (for example `Crazy Taxi`) keep using the indexed game path, per-game settings, and multi-file disc data.
  Keep the matching rule and its tests in the kit so every consumer behaves the same.

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
- The trade-off changes with core count: on the RG DS (4x A55), threaded SCSP was faster in both VF2 (53.7 vs 40.45 fps)
  and Burning Rangers (30.9 vs 25.6 fps). Turning it off removed the sound-thread wait but moved its work onto emulation,
  increased audio underruns, and lowered FPS. Judge the mode by throughput and audio health, not wait time alone.
- On the same Entry device, NiGHTS with 250-349 VDP1 commands/frame measured 48.7 fps with threaded SCSP vs 36.8 fps
  without it (ABBA, same savestate); sound-scheduler time fell 11.29 -> 0.74 ms and underruns 44 -> 31/s. A measured
  per-tier game profile should supersede a stale Auto result, while an explicit per-game On/Off choice still wins.
- Further RG DS A/B/B/A tests with the same savestate confirmed threaded SCSP for VF2 (55.3 vs 41.9 fps at 200-399
  VDP1 commands), Daytona CCE (57.7 vs 44.5) and Guardian Heroes (57.4 vs 47.3), with lower sound-scheduler time and
  fewer underruns in all three. The tested Entry-tier profiles should start with it enabled and supersede stale Auto
  cache results; the user can still choose On or Off per game.
- On the RG DS, Fast SH-2 sync (128 cycles) beat Accurate (32) by 4-10% in NiGHTS, VF2, Daytona and Guardian Heroes
  with the recompiler held off; Burning Rangers stayed at 32 because of `PreciseSH2Sync`. Keep Fast as the app default
  and preserve such core game-database exceptions.
- Approximate SH-2 recompiler On beat Off at Fast sync in the same four games (about 4% in Guardian Heroes, 21% in
  VF2, 24% in NiGHTS and 31% in Daytona). Burning Rangers' precise-sync rule disabled the JIT either way. Keep the
  measured global default On, with the core deciding when the game requires the interpreter.
- Compare both emulated and displayed FPS when evaluating Adaptive graphics: on the RG DS it improved emulation
  throughput and audio underruns in those four games, while reducing the number of rendered frames. In Burning
  Rangers it did not improve throughput or underruns, so GBSat's Entry profile opts out there for smoother video.
- CPU profiles don't show waits: log per frame how long the emulation thread waits for each helper thread (sound,
  VDP1, VDP2) together with the active settings; it found what profiles could not.
- Keep one Android performance hint session, on the emulation thread only; recreating it leaves lasting slowdowns.
- Waiting threads must not spin with `sched_yield` (a syscall per loop): spin briefly with a CPU pause instruction,
  then block (futex / `atomic::wait`).
- Profile-guided optimization (`-fprofile-generate` training build on the device, merged `.profdata` committed to the
  repo, `-fprofile-use` in normal builds) gave ~12% on the interpreter thread at no code cost. Merge the raw profile
  with an `llvm-profdata` of the same LLVM version as the NDK clang.
- A dynarec only pays off when the CPU interpreter thread is the bottleneck at full speed; check the profile first.
- On the Entry tier (RG DS, RK3568: 4x Cortex-A55) Saturn 3D games are bound by the master SH-2 interpreter:
  Virtua Fighter 2's attract runs at ~45 fps with the master at 16.7 ms per frame (6 ms on the T618's A75); the
  thread layout was already right (emulation alone on one core, VDP/sound on the other three). Longer SH-2 sync
  steps barely help there (128 -> 1024 cycles: 21.7 -> 20.1 ms) and 512 made VF2 hang on a black screen at boot:
  keep Fast at 128 cycles.
- Before attributing an Entry-tier CPU bottleneck to thermal throttling, sample the device governor, current/max
  frequency and thermal status during gameplay. On the RG DS, VF2's four A55s reported a shared `performance`
  policy at 1.992 GHz throughout a 25-second CPU-renderer capture; thermal status stayed 0 at 60 °C (70 °C
  threshold). This rules out observed clock scaling and thermal throttling in that capture; affinity and CPU
  utilization are separate measurements and must not be inferred from frequency alone.
- Reusing alternate VDP2 background rows in 448-line interlaced software output (Yaba-style half-height backgrounds)
  was tested on the RG DS with the same VF2 Slot 1 state and adaptive graphics on. 30 s averaged 58.7 -> 58.9 emulated
  FPS, but average frame time rose 16.2 -> 16.9 ms and slow-frame count rose 578 -> 625; the image also lost too much
  vertical detail. The experiment was removed; do not port without repeatable gains that justify the quality loss.
- Test a speed setting by booting each game from the BIOS, not only from save states: Burning Rangers ran from a state
  with the 128-cycle SH-2 sync and deadlocked right after the BIOS with 64+ (both CPUs polling). Per-game exceptions
  go in the core's game database (GBSat: `PreciseSH2Sync`), not in the option's default.
- Measure a borrowed recompiler against the interpreter at the same CPU sync before keeping it: YabaSanshiro's SH-2
  dynarec (template copies, registers kept in memory) ported into Ymir gave nothing over the chained interpreter on the
  T618. Its apparent ~10% came from syncing the two SH-2s every 128 cycles instead of 32, which the interpreter gets
  just as well (Burning Rangers 16.1 -> 14.6 ms per frame; GBSat's CPU sync option). With 32-cycle slices, entering and
  leaving compiled code every slice eats the gain: a JIT needs longer slices and state kept in place (no register copies).

## GPU renderers (Vulkan on Mali)

- Mobile GPUs hate many small dispatches: per-scanline register changes (raster effects) must go to the GPU as
  per-line tables, not as one batch per line (DonPachi: 240 batches → ~6 per frame, 30 → 60 fps).
- Read frames back from **cached** host memory (`HOST_CACHED`, invalidate if not coherent) and read it once:
  uncached reads are very slow on Mali.
- Keep the software renderer: some games stay faster there on weak GPUs; the GPU path wins on Adreno-class devices.
- A sudden burst of draw commands (one heavy second among smooth ones) may be the game's command list looping: count
  the *distinct* command addresses per frame before optimizing the renderer. NiGHTS ran 85 commands ~215 times per
  frame (80-100 ms frames, white sprite smears) because the core gave the VDP1 4x its real cycle budget and it reached
  the loop's jump before the game rewrote the table; the game database's accurate-VDP1-timing flag fixed it.

## Frame pacing

- Automatic frame skip helps only when rendering is the bottleneck; when the CPU emulation is, skipping just makes
  the picture choppy, so back off (stop skipping for a while) when skipping doesn't catch up.
- Fast forward should render ~60 frames per real second and skip the rest.
- Present frames zero-copy through `AHardwareBuffer`.

## Android foreground lifecycle

- Distinguish a player-requested pause from the pause imposed when Android backgrounds the activity. Capture whether gameplay was active before pausing in `onPause()`; on `onResume()`, consume a one-shot flag and resume only if that same game session is still running. If the menu was already open, preserve the intentional pause.
- Prefer explicit pause-state setters to native toggles. A toggle called twice can undo the intended state, so lifecycle recovery must inspect/know the current state and only transition when necessary.
- The background/foreground regression was reproduced and then device-tested successfully in both GBSat and GBCast (October 2026). Keep active-gameplay return and intentionally-paused/menu-open return as mandatory regression cases for future family apps.

## Controller and menus

- Every dialog/menu must work with the controller from the first press: draw dialogs in the app window, focus the
  first item, never let the game `SurfaceView` take key focus, let Android map hat-axis D-pads to D-pad keys while a
  menu is open, and handle global buttons (Select, B in dialogs) in the Activity.
- With targetSdk 36 (predictive back), map the controller's B to `onBackPressedDispatcher.onBackPressed()`.
- Consume every gamepad key the app acts on, down *and* up. An unhandled key makes Android dispatch its fallback key
  (`Generic.kcm`: L3/`BUTTON_THUMBL` → `DPAD_CENTER`, among others). GBSat's L3+X menu hotkey opened the menu, then the
  L3 release came back as `DPAD_CENTER` and pressed "Resume": the hotkey "worked once" and the menu seemed to break
  (Retroid Pocket Nova; Select+X on the RG Rotate was fine because Select has no fallback). `Hotkeys.onKey` now
  returns true for the release of a key that took part in a combination. To debug keys, log `dispatchKeyEvent` and
  inject real pad events with `sendevent /dev/input/eventN` (held presses); `input keycombination` comes from a
  virtual keyboard and doesn't reproduce controller paths.
- During controller-binding capture, consume Android `KEYCODE_BACK` and its matching release without adding Back to
  the binding: some handhelds emit it alongside a physical combo button, and an unhandled companion event runs the
  screen's BackHandler before the chord can be saved.
- Cheats and achievements are shared GBFrame UI components, not app-specific screens: `CheatsScreen` receives a core-specific `CheatsBackend`, while `AchievementsScreen` uses the common `RetroAchievements` client with an app-specific `RaBackend`. Keep parsing, save/apply APIs and hashing in the app/core adapter; standardize presentation and navigation in GBFrame.
- Apply "a physical controller hides the touch controls" when the game screen is created, not when the game has
  started: Flycast's virtual gamepad started visible and GBCast hid it only on the game-started event, so it showed
  over the BIOS boot on handhelds. Touch controls start hidden and the app shows them when there is no controller.
- Overlays drawn over the game must not pad by the system bars (`safeDrawingPadding`): the game is immersive, so the
  bars only show for a moment (GBCast's game Activity hid them on window focus, after its first frames) and in that
  moment the menu button and performance overlay sat a status bar lower. Use `gameOverlayInsets()` (display cutout
  only) or the kit's `GameplayOverlayChrome`, and hide the bars in `onCreate`, not only on focus.
- Starting the library Activity by intent while the app is open (`LaunchIntent`): with the default launch mode an
  intent that `filterEquals` the task's root (same component, extras don't count) only brings the task to the front,
  without `onNewIntent`. GBSat's single Activity is `singleTask`; GBCast's LibraryActivity is `singleTop` (not
  `singleTask`, which would close the game Activity above it when the launcher icon is tapped).
- When a focused overlay drawn in the same window (the quit `ConfirmDialog`, the main menu) goes away, Compose gives the
  focus to the first focusable laid out — on a shelf, the cover partly visible at the edge — and that cover's
  `onFocused` overwrites the selection (Back/B on the quit dialog moved it two covers left). `HomeLibrary` keeps the
  selection while it may not take the focus (`focusEnabled` false or `MenuState.quitOpen`), writes it back, and only
  stops refocusing once the focus is on that game; `LibraryView.lastGame` is snapshot state so the requester follows.
- When a screen leaves composition, clear any focus flags stored outside Compose state. A stale `hasFocus=true` can make focus restoration skip the requester and leave selection behind an overlay. Do not attach one `FocusRequester` to multiple controls when only one should receive initial focus.
- Standardize the in-game quick menu across GB-family apps against GBSat: left-side dimmed panel, same root item order, matching submenu hierarchy, system Back/B stack behavior, pause/resume semantics, and return focus to the item that opened a submenu. Keep the visuals themed and expose only capabilities actually implemented by the app/core; see [QUICK_MENU_STANDARD.md](QUICK_MENU_STANDARD.md).

## Storage, saves and Play

- Auto load should continue from the game's latest save state in any slot, not only the auto save; write the auto
  save also when the app goes to the background (Android may close it there, and the in-game Exit never runs).
- Remember UI state such as the library layout and selected game between sessions; do not persist a removed library mode.

- No `MANAGE_EXTERNAL_STORAGE`: read games through SAF (folder picker + persisted tree URI); path-based cores keep
  working with `/proc/self/fd/N` symlinks in a staging folder.
- Reopening a `/proc/self/fd/N` link *by path* (fopen/ifstream/mmap of the path) is refused on some devices (Anbernic
  RG DS: `stat` works, `fopen` fails, libchdr says "file not found"). Copying the file to the cache worked around it but
  costs minutes and gigabytes. The core's loaders therefore read the staging symlinks through the descriptor itself
  (`readlink` -> `/proc/self/fd/N`) with the kit's `native/gb_proc_fd.hpp` (`gb::ProcFdOf`, `gb::InputFile`, an istream over
  the descriptor; a core gets the include path with `gb_native_core(<core target>)`): GBSat's CHD loader uses
  `chd_open_core_file_callbacks` + `pread`, ISO/CUE/MDS/CCD text and headers use `InputFile`, file mappings use `mio` on the
  descriptor. The copy of a CHD/ISO stays only as a fallback, and `SafGames.openAndLoad` / `useDisc` do the retry for every
  app (the app only supplies the core's load call). Not covered: GBSat's wide-char MDS filename read.
- Export (saves + save states only) / Import, game-named save folders.
- Give games a save (backup memory) cartridge when they don't need a RAM cartridge: some games ask for one.
- Google Play: targetSdk 36, 16 KB pages, AAB in the build artifacts only, edge-to-edge, privacy policy, no console
  maker trademarks in the name or icon.
- On a SAF tree every `DocumentFile.findFile` lists the whole folder (~50 ms each on handhelds); exists/length/
  lastModified through it walked the path again on every call (the save-state menu: ~100 provider queries). List each
  folder once (id, name, type, size, time in one query) and cache it, dropped on every write through the app and after a
  few seconds for outside changes.
- Keep migrations of older layouts only once a version with that layout was released; before that they are dead code
  run at every start (GBSat's state-folder check cost ~1.2 s per launch on the RG Rotate).
- The first data folder (setup wizard, app storage before) can be used at once after the copy; only a switch between two
  picked folders needs a restart. Reopen files the core keeps open (the backup RAM image) at their new location.

## CI

- For every GB-family Android app, set Gradle `namespace` equal to `applicationId` (`app.<app-slug>.emulator`). Source Kotlin/Java packages may differ, but manifest component names must then be fully qualified and JNI export names must follow the real source package. Android external-media paths follow `applicationId`, not the source package.
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
- An SH-2 JIT that gives exactly the interpreter's results (cycle by cycle: load-use stalls, bus waits, slice ends
  checked per instruction, blocks only at jump targets) was ~2x slower than Ymir's chained interpreter on the RG DS:
  the chain costs only ~15-20 host instructions per SH-2 instruction, each block entry ~100, VF2's basic blocks are ~7
  instructions, and whatever follows a block that stopped early ran interpreted (~2M instructions/s). It paid off only
  in an approximate mode: blocks compiled and entered at any instruction, run to their end past the slice (the slice is
  checked between blocks), conditional delayed branches with their slot in the block, memory outside the direct work-RAM
  path through small read/write helpers instead of interpreter calls. RG DS: Daytona USA +24%, DonPachi +11%, VF2 +6%,
  Guardian Heroes +5%, NiGHTS +3%; on the faster T618 the same frame rate with 7-15 points less CPU.
- Find what generated code spends its time on before tuning it: write a map of each block (host address of each part
  and guest instruction) and match simpleperf samples (`--sort dso,vaddr_in_file`, the "unknown" dso) against it. Once
  the generated code is cheap, removing per-instruction work (stall tracking, cycle adds) changes nothing measurable.
- A recompiler loses where slices are short: with 32-cycle SH-2 sync (Burning Rangers' game-database exception) every
  slice enters and leaves compiled code and the JIT was 35% slower than the interpreter. Turn it on only with the long
  (128-cycle) sync and let the core switch it off by itself for games that need the short one.
- Approximate timing changes what a game sees: test each one from a BIOS boot and from gameplay saves, compare the
  image with the interpreter, and keep a per-game way out (the option in the in-game menu, applied live).
- An idle-loop skip can't take a wait loop that calls functions which write memory (a lock in Burning Rangers' and
  Daytona's boot wait at 06067B32): skipping iterations changes the result. Check the loop's code before promising a gain.
- Before optimizing a helper thread check that the emulation waits on it: the RG DS's SCSP thread was ~32% of the CPU
  (half of it the sound DSP), but no game tested waited more than 1 ms per frame on sound.

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
- Users point every emulator at one shared folder (e.g. `Emuladores`): writing mounts at the top of the selected tree
  spilled `data/`, `covers/` and the ownership marker there (and blocked the other apps). Data goes in `<selected>/<app>`.
- The Dreamcast BIOS runs its intro/menu program from main RAM, so "PC in RAM" doesn't mean the game started: wait
  for the disc's IP.BIN bootstrap (8C008000-8C00FFFF with "SEGA SEGAKATANA" loaded there) and end the fast boot when
  execution leaves it. Sample the PC per frame first before choosing a hand-over rule for a new firmware. The Saturn
  BIOS runs its intro from WRAM-H at 06010000+, above most games' 1st read address: "PC >= 1st read" ended GBSat's
  fast boot on the SEGA SATURN logo; remembering the pages the BIOS used before IP.BIN appeared fixed it.
- Overlay colours follow common emulators: FPS and speed green from 95% of full speed (DuckStation and PCSX2/AetherSX2
  also treat below 95% as slow), yellow from 80%, red below.
- Show the library at once: keep the last scan's list and the last save-state list (preferences / memory) and draw them
  while the folder is scanned again in the background; a spinner then "no games" flashed on every start. Don't walk the
  games folder again each time a game closes (once per launch, or after a few minutes).
- Show the app icon while the app starts, not a spinner. Decode what the library's first frames need on the startup
  thread (covers already on disk, the cropped icon: the crop scanned every pixel on the main thread at each use), and let
  the selected cover snap into place for the first ~0.7 s: animations over the slow first frames stutter.
- Ship `androidx.profileinstaller` and a `baseline-prof.txt` (recorded on a device with `cmd package dump-profiles
  --dump-classes-and-methods` after using the app, plus `HSPL<package>/**` wildcards): a sideloaded APK otherwise runs
  its UI interpreted until the device compiles it while idle (first library frames ~300 ms instead of ~110 ms).
- Store covers small (WebP, at most ~600 px on the long side): downloaded PNGs and picked photos were 0.5-3 MB each
  (16 covers 10 MB -> 0.7 MB).
