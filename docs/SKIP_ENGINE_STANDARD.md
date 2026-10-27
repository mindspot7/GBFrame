# Shared automatic Skip Engine

GBFrame owns `gb::AutoSkip` (`native/auto_skip.hpp/.cpp`) and the common three
Loading Times controls: Skip loading screens, Skip transitions, Skip save waits.
Every consumer must integrate these functions when its core supplies the required
signals. Unsupported integrations must document the missing technical capability;
do not add inert switches or implement a second console-specific detector.

## Consumer contract

Use `gb_native_auto_skip(target)` directly, or `gb_native_add(target)` which
includes it. Keep the three persisted flags independent and disabled by default
for new installations. Preserve existing user preferences on upgrade.

- Supply the actual game picture through `OnFrame`, raw audio before frontend
  muting through `OnSample`, and actual emulated disc/backup-memory activity
  through `Update` once per emulated frame.
- Pixel observations may arrive on the renderer thread; the shared analyzer
  synchronizes them with frame updates. Audio peaks use atomic maximum updates.
- Load detection requires recent disc activity, a still/black picture and quiet
  audio. Transitions require consecutive black pictures and silence. Save waits
  require bulk backup activity plus a still/black quiet picture, so background
  autosaves during gameplay are not treated as a waiting screen.
- Run without wall-clock/audio/vsync throttling only while `Active()` is true.
  Mute accelerated output without hiding the unmuted samples from the detector.
  Do not skip emulation, writes, checksums, device commands, or save contents.
- Audio or a moving/non-black picture ends automatic acceleration immediately.
  Missing/stale pictures must not become permission to accelerate indefinitely.
- Reset observations at game start/end, pause/resume, state load, disc change and
  network-mode changes. Block automatic speedup in synchronized online/multiboard
  sessions and while menus are active.
- Keep manual fast-forward separate: automatic detection must neither turn on
  nor clear the user's manual switch. It may share the speed-limit implementation.
- Do not perform GPU readback when all three options are off. GPU-based consumers
  use a tiny downscaled game image on the renderer/context thread, bounded sampling
  and freshness checks; never capture Android's screen or introduce screen-capture
  permissions. Measure readback overhead on a real device and both renderers.

The mechanism accelerates detected emulated waits, not file import, app startup
or loading an emulator savestate. Detection is conservative and heuristic; a wait
with music or a large animation may deliberately remain at normal speed.

`FastDiscOk()` additionally allows cores with configurable disc speed to suppress
that speedup while disc audio/video is streaming, including music over a still
picture. This is separate from the automatic fast-forward decision.

## Integration status and other disc accelerators

GBSat supplies rendered pixels, SCSP audio, CD data reads/seeks and backup-memory
accesses. GBCast supplies sampled OpenGL/Vulkan game pictures, AICA audio, disc
sector reads through both the native GD-ROM and HLE BIOS paths, and VMU/Naomi
EEPROM activity. Both use the same detector and the shared System controls.

GBCast's **Fast GD-ROM load** changes emulated DMA transfer scheduling in
`getGDROMTicks()`; it does not automatically fast-forward the whole machine and
does not cover scene transitions or VMU waits. Keep it as a distinct, explicitly
selected advanced option after the three common controls in Loading Times. Do
not rename it to Skip loading screens, merge their stored flags, or enable it
automatically. Existing games may depend on original drive timing.

## Validation

Run `tools/test-auto-skip.ps1` with an ARM64 Android device. The native tests cover
default-off behavior, each detector, missing pictures, gameplay/audio rejection,
background saves, immediate cancellation, small loading animations, disc streaming,
reset/diagnostics and concurrent audio/render/emulation calls.

Consumer tests also verify settings persistence, independence from manual and
disc-speed switches, lifecycle suppression, real disc/backup signals, rendering
without corruption and bounded observation overhead. Report which actual game
waits and renderers were verified, rather than treating a visible toggle as proof
of native functionality. Diagnostics belong in development builds, not user flows.
