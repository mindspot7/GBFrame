# User-selected app data folder

The user-selected folder is the **authoritative working storage**, not a backup
or a synchronized mirror. Every consumer exposes the shared **Data folder** in
its initial wizard and **System**. Without a selection, app-specific storage is
the default. Games are a separate selection.

The app's data never goes straight into the selected folder: `AppDataFolder` uses
a subfolder named after the app (`<selected>/GBSat`, `<selected>/GBCast`), created
on first use, so one shared folder (e.g. `Emuladores`) can hold every emulator.
If the selected folder already has the app's name it is used as is. Selections
made before storage version 3 (mounts and `.gbframe-app` at the top) are moved
into the app folder at startup; files that already exist there are kept.

Use `ACTION_OPEN_DOCUMENT_TREE` and persistent read/write URI grants. Access the
selected documents through `ContentResolver`; never guess a filesystem path from
a URI or request `MANAGE_EXTERNAL_STORAGE`, legacy storage, or all-files access.
Android's official SAF model supports creating, reading and editing documents in
the selected tree. This storage approach does not require Play's restricted
all-files permission; full app publication remains subject to all other policies.
Sources: https://developer.android.com/training/data-storage/shared/documents-files
and https://support.google.com/googleplay/android-developer/answer/10467955

## Shared implementation

`AppDataFolder.openAtStartup()` opens the selected tree before native initialization.
Consumers supply explicit named mounts, with no console assumptions in GBFrame.
Use `DataFile` for managed Kotlin file operations, including screenshots, BIOS,
cheats and covers. File paths inside a mount are logical identities used by the
adapter, not alternate locations to write data. Never bypass the adapter with
`FileInputStream`, `BitmapFactory.decodeFile`, or libc++ file streams.

Native consumers include `gb_native_storage(target)` and load their library before
opening the folder. The shared native adapter routes mounted stdio/POSIX file,
metadata, directory, rename and deletion calls to SAF-provided descriptors.
Use `gb::DataInputFile` / `DataOutputFile` for streams, `gb::DataStat` for filesystem
metadata, and `gb::DataOpen` for descriptor-based mapping. Libraries whose filesystem
operations execute inside a separate shared library need explicit adapters too.
Do not substitute actual `/sdcard/...` paths for selected document permissions.

All mounted data is read/written in the selected folder. Do not maintain a second
working copy, recursively hash data on every launch, synchronize in the background,
or restore a directory mirror before launching. Normal startup validates access
and mount roots only. System uses the three compact folder rows from
SYSTEM_MENU_STANDARD.md. The full folder setup/recovery controls can offer
**Check access**; there is no **Sync now** operation.

Folder changes and migration may copy/verify data once, with progress feedback and
process restart before switching a live core. Existing destination files are kept.
Migration from the old mirrored implementation first preserves completed offline
writes, verifies each destination, then retires the matching obsolete local files.
Conflicting legacy files are retained only in `data-folder-recovery`, not used as
an active mirror. Interrupted migration must remain retryable without discarding
unsynchronized saves. Never delete an unverified source or silently fall back to
stale local files when the selected storage is unavailable.

If a required operation is unsupported (for example, random-access memory mapping),
report it explicitly and let the user choose supported storage. A temporary copy
is permitted only for a specific, demonstrated unsupported operation, with a
bounded lifetime and an explicit write-back/error policy. It is not the default
storage strategy, and must never become a hidden persistent directory mirror.

## Consumer capabilities and validation

Each app owns its mount names and formats. Never mount credentials, private
preferences, games, temporary disc staging, or disposable caches.
GBSat mounts `bios`, `saves`, `states`, `cheats`, `covers`, `carts`.
GBCast mounts native `data` and library `covers`; its supported texture replacements
live in `data/textures/<game ID>`. Expose Textures only for capable cores.
The `.gbframe-app` marker prevents accidental sharing between different consumers.

Test direct Kotlin and native writes, metadata, reading externally edited files,
rename/replace, directory listing, descriptor mapping, and absence of local copies.
Test real save/load with previews, restart, unavailable storage, migration retry,
path traversal rejection and data preservation. Verify installation updates locally
without uninstalling or clearing user data. Keep installation/configuration and
Android URI grants private as Android requires.
