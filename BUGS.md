# Mochi Backup — Complete Bug Report

> **Status: 27 of 28 fixed** — see [FIXES.md](FIXES.md) for what changed. Only **M-5** is deliberately
> deferred (fixing it would invalidate the integrity data of every existing backup).

**Project:** Mochi 3.0.0 · Fabric · Minecraft 26.1–26.3 · Java 25
**Baseline:** commit `de679e0` plus the dead-code cleanup pass
**Build status:** `gradlew build` → **BUILD SUCCESSFUL** (Loom 1.17.21, Gradle 9.6.0)
**Compatibility:** every finding compiles; none have been runtime-tested.

**Severities**
- **Critical** — data loss, total feature failure, or remote file write.
- **High** — wrong behaviour, silent data risk, or broken access control.
- **Medium** — degraded behaviour or edge-case corruption.
- **Low** — cosmetic, diagnostics, or configuration friction.

**Totals: 4 Critical · 10 High · 9 Medium · 5 Low = 28 findings.**

All `file:line` references are against the current tree.

---

## Critical

### C-1. Sequential ZIP path closes every entry twice and always fails
**Where:** `src/main/java/com/mochi/backup/core/create/compressors/ZipCompressor.java:69` and `:71`

`closeArchiveEntry()` is called inside the `else` (real-file) branch, then again unconditionally after the `if/else`. Verified against commons-compress 1.26.1 bytecode: `closeArchiveEntry()` → `preClose()` → `closeEntry(ZZ)V`, which executes `entry = null`; `preClose()` then throws `IOException("No current entry to close")` because `entry` is null.

**Trigger:** the sequential compressor is selected whenever `coreCount <= 1` **or** TMPFS is disabled (`ExecutableBackup.java:141`). That is: single-core machines/containers, `compressionCoreCountLimit = 1`, a read-only temp dir, or a world larger than 80% of free temp space (`Globals.updateTMPFSFlag`).

**Impact:** the first real file throws. `integrityVerificationMode` defaults to `STRICT`, so `AbstractCompressor.java:54` rethrows, and `ExecutableBackup.java:191-193` deletes the archive. **Every ZIP backup on such a machine fails.** ZIP is the default format.

**Fix:** delete line 71 and ensure each entry is closed exactly once (close inside each branch, or close once after a `putArchiveEntry` that actually happened).

---

### C-2. One vanished file permanently deadlocks the backup executor
**Where:** `src/main/java/com/mochi/backup/core/create/compressors/ParallelZipCompressor.java:38-52`, `core/digest/FileTreeHashBuilder.java:22`/`:31`, `core/create/compressors/AbstractCompressor.java:61`

When `createArchiveEntry` or `Files.size` throws `NoSuchFileException`, `addEntry` returns early — but the `CountDownLatch` is only decremented from `HashingInputStream.close()` → `hashBuilder.update(...)`. A skipped file never reaches close, so the latch never reaches zero. `AbstractCompressor` then calls `getValue(true)` → `latch.await()` with **no timeout**.

**Trigger:** any file removed between the `Files.walk` snapshot and its turn in the loop — exactly the case the code's own comments claim to handle ("player disconnect cleanup, etc.").

**Impact:** the single-thread executor (`Globals.java`) is wedged forever. Every later `/mochi start`, timer backup and shutdown backup silently queues and never runs. `resetQueueExecutor()` refuses to replace a live executor, so it stays broken for the whole JVM session.

**Fix:** count the latch down in a `finally`, or skip the `await` and validate with a bounded timeout.

---

### C-3. Zip Slip / Tar Slip — arbitrary file write during restore
**Where:** `src/main/java/com/mochi/backup/core/restore/decompressors/ZipDecompressor.java:29`, `core/restore/decompressors/GenericTarDecompressor.java:36`

Both do `target.resolve(entry.getName())` with no `normalize()` and no `startsWith(target)` containment check, then create directories and write to the resulting path.

**Trigger:** a crafted or modified archive containing `../../x`, an absolute path, or a Windows drive path (`C:/...`).

**Impact:** files written anywhere the server process can write, during `/mochi restore`. The deprecated `RestoreManager` (now deleted) had exactly this guard; it was dropped in the rewrite.

**Fix:** `Path out = target.resolve(name).normalize(); if (!out.startsWith(target)) throw new IOException("zip slip: " + name);`

---

### C-4. Restore discards the integrity result, then destroys the world with no rollback
**Where:** `src/main/java/com/mochi/backup/core/restore/RestoreBackupRunnable.java:54-66` (result unused), `:109-110` (delete + move)

`errorMsg` is assigned from `CompressionStatus.validate(...)` three times and **never read**. After that, `Utilities.deleteDirectory(worldFile)` deletes the live world and `Files.move(tmp, worldFile)` installs the extracted one. There is no `level.dat` check, no verification, and no rollback if the move fails.

**Trigger:** restoring any corrupt, truncated, foreign, or legacy archive — including one the code knowingly accepts ("No integrity status file. Proceeding anyway.").

**Impact:** the world is deleted before the replacement is known good. If the move fails, the world is gone; the only copy is the safety backup. This directly contradicts the README's "automatic rollback … your original world stays intact".

**Fix:** gate on `errorMsg`, validate `level.dat` in the temp dir, rename the old world instead of deleting, and restore it on failure.

---

## High

### H-1. Inverted date comparison makes integrity validation impossible
**Where:** `src/main/java/com/mochi/backup/core/CompressionStatus.java:32`

```java
if (ctx.restoreableFile().getCreationTime().equals(date))
    return Optional.of("Creation date mismatch!...");
```

It reports a mismatch precisely when the dates are **equal**. For every backup this mod creates, the filename timestamp and the stored `date` are identical.

**Impact:** `validate()` always returns an error for a healthy backup. Currently masked by C-4; the moment C-4 is fixed, every restore is blocked. `IntegrityVerificationMode.verify()` is defined but never called anywhere.

**Fix:** negate the condition.

---

### H-2. Automatic backups never enforce retention — they clean the wrong folder
**Where:** `src/main/java/com/mochi/backup/core/create/ExecutableBackup.java:150`, `core/Cleanup.java:29`, `Utilities.java:51-85`

Timer and shutdown backups have `commandSource == null`. `Cleanup` then substitutes `Path.of(System.getProperty("user.home"))` as the *server directory*; `getBackupRootPath` → `findGameRoot` walks up looking for a `config` folder and falls back to the parent of `user.home`, so the root resolves to something like `C:\Users\mochi_backups\<world>` — and even creates it.

**Impact:** `backupsToKeep`, `maxAge` and `maxSize` are never enforced for automatic backups. The backup folder grows until the disk fills. Only player-initiated `/mochi start` and `/mochi cleanup` work correctly.

**Fix:** pass the real server directory (or the already-resolved backup root) instead of deriving it from a possibly-null command source.

---

### H-3. The "safety backup" runs synchronously on the server tick thread, plus a hard 10-second stall
**Where:** `src/main/java/com/mochi/backup/core/restore/RestoreHelper.java:51`, `core/create/ExecutableBackup.java:113`

`RestoreHelper.create` calls `ExecutableBackup...build().call()` inline, and commands run on the server thread. Inside, the save is queued onto that same thread via `executeIfPossible`, then the code spins `for (int i = 0; i < 200 && server.isRunning(); i++) Thread.sleep(50)`. That condition can never become false during normal operation, so it always sleeps the full 10 s — during which the queued save cannot run — and then compresses the world on the tick thread.

**Impact:** `/mochi restore` freezes the server for ~10 s plus the entire backup duration, and the "safety" backup predates the save it asked for (stale player/world data).

**Fix:** run the safety backup on the executor and wait for a completion latch; wait on the save task, not on `isRunning()`.

---

### H-4. Partial backups are retained and restorable outside STRICT mode
**Where:** `src/main/java/com/mochi/backup/core/create/ExecutableBackup.java:191-193`

The output file is created up front (`:131`) and deleted on failure **only** when `integrityVerificationMode.isStrict()`.

**Trigger:** disk full or any exception mid-compression with `PERMISSIBLE` or `VERY_PERMISSIBLE` (both offered in the config UI, `MochiConfigScreen.java:85`).

**Impact:** a truncated archive remains under a perfectly valid backup name, appears in `/mochi list`, and can be restored (see C-4).

**Fix:** always delete the partial file on failure; make non-strict affect only the broken-file policy.

---

### H-5. Command permission check fails open
**Where:** `src/main/java/com/mochi/backup/MochiClient.java:130-136` and `:150-165`

The entire `.requires(...)` predicate is wrapped in `catch (Exception ignored) { return true; }`, and `canPlayerRunCommand` maps only levels 0–4 — anything else hits `default -> null` and returns `true`.

**Trigger:** `permissionLevel: 5` (or any invalid/typo'd value), or any exception inside the predicate.

**Impact:** **every player** passes the check and can run `/mochi restore` and `/mochi delete`. Also, `ctx.getTextName()` means command blocks and functions are evaluated against the player whitelist/blacklist as if they were players.

**Fix:** fail closed (`return false`), validate `permissionLevel` in `validatePostLoad`, and treat non-player sources as server console.

---

### H-6. Restore/delete lock is defeated by `Path` identity comparison
**Where:** `src/main/java/com/mochi/backup/core/Cleanup.java:83`, `commands/manage/DeleteCommand.java:47`

Both use `filter(p -> p == f)` / `filter(p -> p == foundFile)` — reference equality on `Path`. The locked path and the freshly listed path are different objects.

**Impact:** the "cannot delete — being restored" guard never fires. A backup can be deleted while it is being read/restored. On Windows this usually surfaces as a sharing violation; on other filesystems the source is deleted mid-read.

**Fix:** compare with `.equals()` (or compare `toAbsolutePath().normalize()`).

---

### H-7. Config migration is dead code, and the serialiser reads a different file
**Where:** `src/main/java/com/mochi/backup/MochiClient.java:32` (register), `:166-219` (`migrateConfig`)

`migrateConfig()` parses `config/mochi.json` into `CONFIG`, then `AutoConfig.register(...)` immediately overwrites `CONFIG` with its own instance. Verified in the pinned `cloth-config-fabric-26.1.154.jar`: `JanksonConfigSerializer` uses **`mochi.json5`**, while `GsonConfigSerializer` uses `.json`.

**Impact:** the `mochi.json` that migration writes is never read back. A user upgrading with only `mochi.json` (which is exactly what this repo's own `run/config/mochi.json` has, i.e. every v1/v2 install) silently gets all defaults: path, interval, retention, blacklist, Discord settings.

**Fix:** migrate `mochi.json` → `mochi.json5` (or switch to `GsonConfigSerializer`), and delete the dead partial loader.

---

### H-8. Restore can race an in-flight backup
**Where:** `src/main/java/com/mochi/backup/core/restore/RestoreBackupRunnable.java` (own thread), `commands/restore/RestoreBackupCommand.java:48`

The restore runs on a dedicated thread and never cancels or joins the single-thread executor. The command only guards against a second *restore*, not a running *backup*.

**Impact:** a timer backup still walking the world when the restore deletes it produces a truncated archive — or triggers C-2's deadlock.

**Fix:** introduce a global operation lock (one backup/restore at a time).

---

### H-9. Restore wait has no timeout
**Where:** `src/main/java/com/mochi/backup/core/restore/RestoreBackupRunnable.java:86-89`

`FutureTask.get()` on `t.join()` blocks indefinitely. The 10-second fallback at `:92-95` only runs if an exception is thrown, not if the server thread simply never terminates.

**Impact:** a mod/hook keeping the server thread alive after `halt(false)` hangs the restore thread forever; the temp world is never installed and the JVM cannot exit.

**Fix:** `task.get(timeout, TimeUnit.SECONDS)` and fail into the rollback path.

---

### H-10. Unsafe deserialisation of archive-supplied status data
**Where:** `src/main/java/com/mochi/backup/core/CompressionStatus.java:40`

`ObjectInputStream.readObject()` runs on `mochi_status.data` taken directly from the archive, with no `ObjectInputFilter` or class allow-list.

**Impact:** arbitrary deserialisation inside the server process from a crafted backup. RCE impact depends on a usable gadget on the modded classpath — the vector itself is certain, the exploitability is not proven.

**Fix:** replace Java serialisation with a JSON/NBT status record.

---

## Medium

### M-1. NPE inside the backup failure handler
**Where:** `src/main/java/com/mochi/backup/core/create/ExecutableBackup.java:188`

`e.getMessage().substring(0, Math.min(e.getMessage().length(), 1024))` — `getMessage()` is null for most NPEs and many RuntimeExceptions.

**Impact:** the original failure is replaced by an NPE thrown inside `catch`; the partial archive is not deleted, the player is never told, and the Discord failure notification is lost.

**Fix:** null-safe message formatting.

---

### M-2. A same-second collision deletes a different, valid backup
**Where:** `src/main/java/com/mochi/backup/core/create/ExecutableBackup.java:131` + `:191-193`

`Files.createFile(outFile)` throws `FileAlreadyExistsException` when a backup with the same timestamp/comment already exists; the catch then deletes `outFile` — the earlier, valid archive — in STRICT mode.

**Trigger:** two `/mochi start` calls (or a timer backup plus a manual one) within the same second.

**Fix:** use a unique suffix / `CREATE_NEW` with a retry, and never delete a file this invocation did not create.

---

### M-3. Failed restore leaves shutdown backups disabled and leaks the temp world
**Where:** `src/main/java/com/mochi/backup/core/restore/RestoreBackupRunnable.java:25`, `:33`, `:49`, `:123`

`globalShutdownBackupFlag` is set to `false` at the start and restored to `true` only on the success path. The early returns (temp-dir failure, decompress failure) skip that, and the temp directory is never cleaned.

**Impact:** after a failed restore, the rest of that session produces **no shutdown backup at all**, plus an orphaned world copy on disk.

**Fix:** `try/finally` around the whole body.

---

### M-4. Hash is published before it is mutated (visibility race)
**Where:** `src/main/java/com/mochi/backup/core/digest/FileTreeHashBuilder.java:22-27` and `:31-40`

`latch.countDown()` is called **before** the `synchronized` block that mutates `hash` / `filesProcessed` / `filesTotalSize`, and `getValue()` reads those fields outside the lock after `await()`.

**Impact:** the waiting thread can observe a partially updated state and store a tree hash that does not match the archive — a good backup gets reported as "Tree Hash mismatch" later.

**Fix:** mutate inside the lock first, then `countDown()`; or read under the same lock.

---

### M-5. The tree hash is weak and collision-prone
**Where:** `src/main/java/com/mochi/backup/core/digest/FileTreeHashBuilder.java:24`, `core/digest/HashingOutputStream.java:35`, `core/digest/HashingInputStream.java:46`

Per-file hashes are combined with `hash ^= newHash` (order-independent XOR multiset), and each per-file hash covers only `path.getFileName()`, not the relative path.

**Impact:** swapping two files, or moving a file between `region/` and `DIM-1/region/`, leaves the tree hash unchanged — the integrity check cannot detect it.

**Fix:** hash the relative path as well, and combine sequentially or with a sorted list.

---

### M-6. Parallel ZIP leaks its thread pool and scatter files on the failure path
**Where:** `src/main/java/com/mochi/backup/core/create/compressors/ParallelZipCompressor.java:24`

A non-daemon `newFixedThreadPool(coreLimit)` is created per backup. It is shut down inside `ParallelScatterZipCreator.writeTo()`, reached only from `finish()` — which `AbstractCompressor` calls *after* the hash wait and after any strict-mode rethrow. `AbstractCompressor.close()` is a no-op.

**Impact:** repeated failing parallel backups leak non-daemon threads (preventing JVM exit) and temp scatter files.

**Fix:** override `close()` to shut the executor down, or create it in a try/finally.

---

### M-7. A vanished file is reported as "disk full"
**Where:** `src/main/java/com/mochi/backup/core/create/FileInputStreamSupplier.java:36`

`get()` swallows the `IOException` and returns `null`; the worker then fails on the null payload, and `ParallelZipCompressor` wraps every `ExecutionException` as `NoSpaceLeftOnDeviceException`.

**Impact:** misleading diagnostics, and the file is not recorded in the broken-file list.

**Fix:** propagate the real exception; only map genuine `ENOSPC` to the no-space type.

---

### M-8. Off-thread world save during shutdown backup
**Where:** `src/main/java/com/mochi/backup/core/create/ExecutableBackup.java:118-122`

If the save task was queued but `server.isRunning()` is false, `saveAllChunks(...)` and `WorldSavingState.disable(...)` run on the **executor** thread after the server has stopped and closed storage. Exceptions are swallowed.

**Impact:** data race / use-after-close on chunk maps and storage during shutdown.

**Fix:** skip the fallback save entirely once the server has stopped.

---

### M-9. File blacklist never matches paths on Windows
**Where:** `src/main/java/com/mochi/backup/Utilities.java:92`

`path.toString().endsWith("/" + b)` — on Windows `Path.toString()` uses `\`, so path-style entries never match. Directory entries also never exclude files inside them on any OS.

**Impact:** `fileBlacklist: ["logs/latest.log"]` is silently ignored on Windows.

**Fix:** compare normalised relative paths with `Path` semantics, not string suffixes.

---

## Low

### L-1. Discord embed metadata is invalid / mislabelled
**Where:** `src/main/java/com/mochi/backup/DiscordWebhook.java:39-40`

`timestamp` is a local-time string with a `Z` suffix (claims UTC, isn't), and `footer_text` is a top-level field Discord ignores (it must be `"footer": {"text": ...}`). One non-daemon thread per notification can also delay shutdown by the 10 s connect timeout.

**Fix:** use `Instant.now().toString()` and the nested footer object; use a shared executor.

---

### L-2. Legacy backups cannot be parsed by filename
**Where:** `src/main/java/com/mochi/backup/core/RestoreableFile.java:38-63`

Parsing expects `<dateTimeFormat>[#comment].<ext>`. The v1.0.0 backups present in `run/` are named `mochi_<epochMillis>_<comment>.zip`, so parsing falls through to file-creation time.

**Impact:** `/mochi list` shows them with wrong timestamps; restore-by-timestamp behaves inconsistently.

**Fix:** add a legacy pattern parser.

---

### L-3. Config validation rejects a value the code already clamps
**Where:** `src/main/java/com/mochi/backup/MochiConfig.java:68`

`validatePostLoad` throws when `compressionCoreCountLimit > availableProcessors`, but `ExecutableBackup.java:133-135` already uses `Math.min(...)`.

**Impact:** a perfectly workable config is rejected at load.

---

### L-4. Cloth Config is required but declared optional
**Where:** `src/main/java/com/mochi/backup/MochiClient.java:32`, `src/main/resources/fabric.mod.json:16-20`

`AutoConfig.register(...)` runs unconditionally in `onInitialize`, but `fabric.mod.json` lists only `fabricloader`, `fabric-api` and `minecraft` in `depends`.

**Impact:** launching without Cloth Config produces a `NoClassDefFoundError` crash instead of a clean "missing dependency" message. README calls it "Recommended".

**Fix:** add `cloth-config` to `depends` (or guard the config registration).

---

### L-5. Same-second backups are ordered and selected ambiguously
**Where:** `src/main/java/com/mochi/backup/core/RestoreableFile.java:72`

`compareTo` compares only second-resolution timestamps, and restore/delete lookups filter by timestamp only.

**Impact:** two backups in the same second (different `#comments`) sort arbitrarily and are selected arbitrarily.

---

## Documentation vs. reality

| Claim | Reality |
|---|---|
| "Automatic rollback … original world stays intact"; "current world is renamed and kept as a backup" | No rename, no rollback — `RestoreBackupRunnable.java:109-110` **deletes** then moves |
| `/mochi list` shows clickable **[Restore]** / **[Delete]** buttons | `ListBackupsCommand` prints plain text; no `ClickEvent` anywhere |
| CHANGELOG v3.0.0: "Captures player XYZ via reflection, patches `level.dat` spawn", "`NbtAccounter.unlimitedHeap()`" | No NBT / `level.dat` / spawn code exists in the project |
| "Backup on shutdown" for multiplayer/servers | Skipped unless `server.isSingleplayer()` — `MochiClient.java:65` and `:103` |
| "All commands work with or without cheats" | `permissionLevel` defaults to 4 (`MochiConfig.java:26`); no singleplayer bypass |
| "10-second countdown" | Default `restoreDelay = 30` (`MochiConfig.java:13`) |
| `modrinth-description`: `/mochi backup [comment]` | Literal is `start` |
| "Very Permissible skips checks" | The restore side never consults the mode at all |

---

## Already fixed in this pass

- `MochiClient.java` had a **UTF-16LE timestamp pasted after the closing brace** (NUL bytes throughout the tail) — a hard compile error. Repaired.
- 4 unused classes deleted (`RestoreManager`, `BackupManager`, root `AwaitThread`, `SafeFileCopier`) — 467 lines.
- 6 dead members deleted (`Globals.disableWatchdog`, `Globals.shutdownQueueExecutor`, `ArchiveFormat.getLastPiece`, `MochiLogger.sendHint`, `FileTreeHashBuilder.getRemaining`, `BrokenFileHandler.valid`).
- Unused assets deleted; `MochiLogo.png` installed as the primary mod icon.
- Side effect: the `main` entrypoint no longer touches `net.minecraft.client.Minecraft`, removing the dedicated-server class-loading risk.

---

## Checked and found NOT to be bugs

- `ParallelScatterZipCreator.writeTo()` **does** shut its executor down and recomputes CRC/size for the in-memory status entry — so the parallel path leaks no threads on success and writes a valid CRC.
- `IOUtils.readFully` returns `int` in commons-compress 1.26.1, so `GenericTarDecompressor` compiles as written.
- `Cleanup`'s age arithmetic is not timezone-broken: both sides are local wall-clock treated as UTC, so the offset cancels (DST off by at most 1 h).
- `build.gradle` without an explicit `mappings` declaration is fine — Loom 1.17.21 resolves mappings itself; the project builds.
- `zipFile.close()`/`Files.walk` usage and the try-with-resources nesting are correct.

---

## Suggested fix order

1. **C-1, C-2** — make backups actually work everywhere.
2. **C-4, C-3, H-1, H-3, H-9** — make restore safe (verify → rename → rollback; containment check; async safety backup).
3. **H-5, H-6, H-10** — access control and deserialisation.
4. **H-2, H-4, H-7** — retention, partial files, config migration.
5. **M/H remainder**, then documentation.