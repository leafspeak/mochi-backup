# Mochi Backup v3.0.0 — MC 26.3

Fixes restore freezing, missing player data, broken timer backups, and wrong config loading.

## What Changed

| Issue | Fix |
|-------|-----|
| Stuck on "Saving world" after restore countdown | Replaced `stopServer()` with `halt(false)` — exits in ~1s instead of 28s freeze |
| Empty inventory after restore | Added `playerList.saveAll()` to backup save phase |
| Player spawns at wrong position | Patch `level.dat` spawn `Data.spawn.pos` IntArray to captured XYZ |
| Auto timer backup not running | Config now reads `mochi.json5` → converts to `mochi.json` on startup |
| Backups saving to wrong folder | Walk up from world dir to find game root (`findGameRoot()`) |
| Timer stops working after restore | Preserve executor across restores; auto-recovery fallback added |
| `backupBeforeRestore` toggle broken | Now runs safety backup before every restore |
| NPE in Cleanup on shutdown backup | Null-safe `ctx` check in `getBackupRootPath()` |
| Double `[Mochi] Mochi:` chat prefix | Stripped redundant prefix from broadcast messages |

## What's New

- **Discord webhook** — rich embeds on backup start/done/failed + restore events
- **Safety backup** — automatic backup created before every restore (configurable)
- **JSON5 config support** — both `mochi.json` and `mochi.json5` work

## How It Works

```
Backup:  playerList.saveAll() → saveChunks() → compress world folder → ZIP
Restore: halt(false) → wait for thread → delete old world → move backup in place
Timer:   scheduler ticks every END_SERVER_TICK, submits to single-thread executor
```

## Build

```bash
./gradlew clean jar
# Output: build/libs/mochi-3.0.0.jar
```

Requires: Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, MC 26.3, Java 25
