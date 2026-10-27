# System menu standard

Consumers use GBFrame `SystemSettings` instead of building their own ordering.
The shared component owns these sections, in order:

1. **Data**: exactly **Data folder**, **Games folder**, **Bios folder**, in that order.
2. **BIOS emulation**: HLE BIOS and related firmware emulation, only when supported.
3. Emulator-specific file options, such as cartridge ROM imports.
4. **Save states**: common Auto save and Auto load.
5. **Loading Times**: Fast boot, Skip loading screens, Skip transitions, Skip save waits,
   in that order, followed by additional supported loading options.
6. Remaining emulator-specific settings.

Do not show Getting started or Paths as substitute headings in System. Wizard
steps may retain their onboarding-specific headings. Do not duplicate the moved
startup controls in the main Performance page. In-game quick-menu shortcuts are
independent of the main System-page ordering.

Folder rows show an informative description when unconfigured and the effective
path once configured. Paths must wrap without truncation; never display a BIOS
import-source path as if the core were reading from it. The BIOS row also lists
each required firmware filename with **Loaded** or **Missing**, including partially
installed multi-file firmware. HLE does not turn a missing BIOS into Loaded.
Use shared `SystemFolderSetting`, `SystemBiosFolderSetting` and `SystemBiosFile`.
The consumer supplies its own validated filenames, supported alternatives and
real core actions; GBFrame must not assume any console or BIOS filename.

`DataFolderControls(compact = true)` keeps the System Data section to three
entries, without duplicating wizard paragraphs, texture explanations or a fourth
Check access row. Selection errors and migration feedback remain available.
BIOS selection may offer folder import and individual-file selection in a shared
dialog. Imported firmware lives in the actual active data folder, using the
existing SAF adapters and safe replacement policy. Multiple valid BIOS candidates
must be chosen explicitly rather than picked arbitrarily.

`SystemStartupOptions` exposes only supported functions. Bind toggles to existing
persisted settings and core behavior; do not add inert controls or relabel a
function with different behavior as a missing common option. GBSat supplies Fast
boot and the three Skip Engine settings. GBCast supplies the same three shared
Skip Engine functions, followed by its separate Fast GD-ROM load option.
Keep HLE separate from Fast boot. Integrate the three common skip functions
according to [SKIP_ENGINE_STANDARD.md](SKIP_ENGINE_STANDARD.md) whenever possible.

Validate empty-folder hints, selected paths, all per-file BIOS states, optional
capability visibility, section order, toggle persistence, folder/file pickers and
physical controller/touch navigation on a compact screen. Use the shared layout
in every consuming app; extra functions belong in the supplied extension slots.
