# User-selected app data folder

Every consumer exposes GBFrame's **Data folder** step in the first-run wizard and
`DataFolderControls` in **System**. Keeping app storage is a valid default. The
games folder is a separate read-only selection.

Use `ACTION_OPEN_DOCUMENT_TREE` with persistent read/write URI permission. Never
request all-files access, legacy storage, or convert document URIs into assumed
filesystem paths. Android controls picker restrictions and the app installation.

`AppDataFolder` owns selection, permissions, restoration, verification, status and
synchronization. Each consumer explicitly mounts its data directories; GBFrame
must not assume a console, BIOS filename or save format. Do not mount credentials,
private preferences, games, temporary disc staging or caches. Choose a dedicated
folder for each app; `.gbframe-app` prevents mixing consumers in the same folder.
Consumers with texture-replacement support also include their **Textures** folder
inside a mounted directory and explicitly provide its relative path to the shared
UI. Do not expose texture packs for cores that do not implement replacement.
GBCast uses `data/textures/<game ID>`; GBSat currently has no replacement capability.

Path-based cores use a local working copy. Restore **before native initialization**
on a worker thread, with loading feedback. Selecting a folder seeds missing files,
then requires closing/reopening the process before restoring existing files.
Never replace live core files. Synchronize completed local writes, observe new
subdirectories, debounce bursts, and flush after native shutdown before process
termination. System exposes **Sync now** and errors; the library also warns of
failed access. Synchronization must not depend on enabling automatic save states.

Verify transferred content and stage replacements. Failed transfers preserve the
local working copy and do not advance the failed file's synchronization record.
Unsynchronized local saves survive restart and take precedence over stale external
copies. On simultaneous edits, keep the replaced external version in app-private
`data-folder-recovery`; explicit restores also keep replaced local versions there.
Recovery files are local and do not replace an independent backup. Do not propagate
deletions: a missing file or unavailable provider must not erase a save.

The folder contains mounted data, not a clone of the install. GBSat mounts `bios`,
`saves`, `states`, `cheats`, `covers`, `carts`. GBCast mounts its native `data`
directory and library `covers`. Settings and Android URI grants remain private.

Before removing Export/Import UI, verify physical-device writes, restoration,
persisted grants and unavailable-storage behavior. Preserve a migration path for
legacy layouts that differ from the mounted layout. Automated tests cover nested
restoration, offline edits, write failures/retry, conflicts, recovery and traversal.
