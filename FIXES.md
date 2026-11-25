# Mochi — Fix Report

Companion to [BUGS.md](BUGS.md). Every finding from that report is listed below with what was
changed. **27 of 28 are fixed; one is deliberately deferred** (see the end) because fixing it
would invalidate the integrity data of every backup already on disk.

**Build:** `gradlew build` → BUILD SUCCESSFUL · jar 140.1 KB · Loom 1.17.21 · Gradle 9.6.0 · JDK 25
**Verification:** 19/19 standalone checks pass (see "Verification" below).

---

## Critical

| ID | Fix |
|----|-----|
| **C-1** | `ZipCompressor.addEntry` restructured: exactly **one** `closeArchiveEntry()` per opened entry (closing twice threw "No current entry to close" and failed every sequential ZIP backup). The in-memory status blob is now read with `readNBytes()` instead of a single `read()`, which could return a short buffer and write a corrupt STORED entry. |
| **C-2** | Three layers: (1) `FileTreeHashBuilder` now releases the latch **after** mutating the accumulator, waits with a **5-minute bound** instead of forever, and ignores duplicate reports for the same file; (2) both ZIP compressors call the new `accountSkippedFile()` when a file vanishes, so the latch is always released; (3) the file is recorded as broken so the archive is flagged incomplete. A vanished file can no longer wedge the backup executor. |
| **C-3** | New `Utilities.resolveArchiveEntry()` resolves an entry against the extraction root and rejects `../` traversal, absolute paths and drive paths. Both `ZipDecompressor` and `GenericTarDecompressor` use it. |
| **C-4** | `RestoreBackupRunnable` rewritten. The integrity result is now acted on, the extracted world must contain `level.dat`, the current world is **renamed aside instead of deleted**, the new world is moved in and verified, and the old world is restored automatically on any failure. The old world is deleted only after success. |

## High

| ID | Fix |
|----|-----|
| **H-1** | `CompressionStatus.validate()` compares correctly (it was inverted), at second precision (nanosecond mismatch would have made it always fail), and only when the timestamp actually came from the file name. New `RestoreableFile.isTimeFromName()`. |
| **H-2** | `Cleanup` now takes an explicit server directory; `ExecutableBackup` and `CleanupCommand` pass the real one. Automatic backups no longer clean a phantom `C:\Users\mochi_backups` folder. |
| **H-3** | The safety backup moved out of `RestoreHelper.create()` (which runs on the tick thread) into the restore thread. The fixed `200 × 50 ms` sleep was replaced by a `CountDownLatch` that completes when the save actually finishes. |
| **H-4** | A failed or partial archive is now always deleted, in every integrity mode — never left behind under a valid backup name. |
| **H-5** | The permission predicate fails **closed**; invalid `permissionLevel` values map to the most restrictive level; non-player sources (console, command blocks) are governed by permission level only; the whitelist no longer blocks the console. `alwaysSingleplayerAllowed` now actually means the singleplayer owner can always run the commands. |
| **H-6** | New `Utilities.isSameFile()` (absolute + normalised comparison) used by `Cleanup` and `DeleteCommand` instead of `==`. The restore lock now works. |
| **H-7** | `migrateConfig()` rewritten: it converts an existing `config/mochi.json` **to `mochi.json5`** — the file `JanksonConfigSerializer` actually reads — remapping the legacy `whitelist`/`blacklist` keys. The dead partial loader is gone. Upgrades no longer silently lose all settings. |
| **H-8** | `Globals` gained `backupRunning` / `restorePending`. A restore is refused while a backup runs, backups are paused during a restore (scheduler and `/mochi start`), and the restore thread waits (bounded) for an in-flight backup before touching the world. |
| **H-9** | The server-thread join now uses `FutureTask.get(60, SECONDS)`; a stuck server thread can no longer hang the restore forever. |
| **H-10** | `CompressionStatus.readFromFile()` installs an `ObjectInputFilter` allow-list (and verifies the payload type), so a status blob taken from an archive can only deserialise the handful of classes a genuine status file contains. |

## Medium

| ID | Fix |
|----|-----|
| **M-1** | New `describe(Throwable)` — null-safe, bounded. A message-less exception no longer throws an NPE inside the failure handler. |
| **M-2** | New `reserveUniqueFile()` appends `-2`, `-3`, … when a backup name already exists, and the failure path deletes **only** a file this invocation created. A same-second collision can no longer destroy an earlier valid backup. |
| **M-3** | The whole restore body is wrapped in `try/finally`: the temp world is always cleaned up, and `restorePending`, the file lock and `globalShutdownBackupFlag` are always released. |
| **M-4** | `FileTreeHashBuilder.update()` mutates under the lock and then counts down; `getValue()` snapshots under the same lock. No more hash visibility race. |
| **M-5** | **Deferred — see below.** |
| **M-6** | `ParallelZipCompressor` keeps its own `ExecutorService`, uses daemon workers, and shuts the pool down in `close()` (called from a `finally`), so a failed backup can no longer leak threads that block JVM exit. |
| **M-7** | `NoSpaceLeftOnDeviceException` is only raised when the cause chain actually mentions space; otherwise the real exception is logged. A vanished file is no longer reported as "disk full". |
| **M-8** | The off-thread shutdown save fallback was removed — no more chunk/storage access from the executor thread after the server stopped. |
| **M-9** | `Utilities.isBlacklisted()` normalises `\` to `/` before matching, and supports both file names and relative paths. Path-style blacklist entries now work on Windows. |

## Low

| ID | Fix |
|----|-----|
| **L-1** | Discord embeds now send `Instant.now()` (real UTC) and a proper nested `footer` object, and notifications use one shared **daemon** thread instead of one non-daemon thread each. |
| **L-2** | `RestoreableFile.build()` parses the legacy `mochi_<epochMillis>[_<comment>].<ext>` naming scheme. |
| **L-3** | The over-strict `compressionCoreCountLimit` validation was removed — the value is already clamped with `Math.min()` before use. |
| **L-4** | `cloth-config` is now declared in `depends` and `modmenu` in `recommends`, so a missing config library produces a clean "missing dependency" message instead of a `NoClassDefFoundError` crash. |
| **L-5** | `RestoreableFile.compareTo()` breaks timestamp ties by file name, and `/mochi restore <timestamp> [comment]` can now disambiguate two backups taken in the same second by comment. |

Also fixed while passing through: the README/Modrinth claims that did not match the code (clickable
chat buttons, 10-second countdown, restore order, `/mochi backup`, Cloth Config "recommended").

---

## Deferred: M-5 (weak tree hash)

The tree hash combines per-file hashes with XOR and only hashes each file's **base name**, so
swapping two same-named files or moving one between dimensions is not detected.

**Why it is not fixed here:** the hash is stored *inside every archive*. Changing the algorithm
makes every existing backup fail its integrity check — and since the integrity result is now
enforced (C-4), Strict mode would refuse to restore backups that are perfectly fine. That would
break exactly the thing this task was told not to break.

**How to fix it safely, later:** introduce a status format version, compute both the legacy and a
strong hash (relative path + sequential combine), store both, and accept either for archives that
only carry the legacy value. New backups then get real tamper detection without invalidating old
ones. This is a self-contained change to `FileTreeHashBuilder` + `HashingInputStream` /
`HashingOutputStream` + `CompressionStatus`.

---

## Behaviour changes to be aware of

1. **Strict integrity mode now blocks restores** of archives that fail their check (it is the
   default). This is the intended semantics, but it is a visible change — set *Integrity Mode* to
   *Permissible* to override.
2. **Backups are paused while a restore is pending**, and a restore is refused while a backup is
   running.
3. **Partial/failed archives are always deleted**, never left as a valid-looking backup.
4. **Name collisions get a suffix** (`…-2.zip`) instead of overwriting.
5. **Cloth Config is now a hard dependency.**
6. **Restore renames the old world aside** and rolls back on failure, instead of deleting first.

---

## Verification

* `gradlew build` → **BUILD SUCCESSFUL** (no compile errors, no new warnings beyond the
  pre-existing deprecation note).
* A standalone verification program was compiled against the project's real runtime classpath and
  **19/19** checks passed:
  * `CompressionStatus` serialise → deserialise round-trip **through the new `ObjectInputFilter`**
    (tree hash, date, broken-file map, version, timestamps);
  * zip-slip guard: normal nested entry allowed, `../` rejected, absolute/drive path rejected;
  * blacklist: path-style entry matches on Windows separators, filename entry matches;
  * backup names: new-style with comment parsed, legacy `mochi_<epoch>_<comment>` parsed with the
    right comment and correctly marked as "not from name time";
  * `isSameFile` equal/different path cases.
* The verification program and its scratch files were removed afterwards; the repository contains
  only source, docs and the built jar.