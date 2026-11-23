# Mochi Backup â€” Changelog

---

## ðŸ¡ v3.0.0 â€” Full Restore Fix & Config Overhaul
All critical restore bugs are fixed. Timer backups, config loading, and player data now work perfectly.

### âœ¨ Features
- **Discord Webhooks** â€” Rich embed notifications on backup start/done/failed and world restore events
- **JSON5 Config Support** â€” Edit `mochi.json5` in-game; mod auto-converts to `mochi.json` on startup
- **Safety Backup Before Restore** â€” Automatic backup of current world before every `/mochi restore`
- **In-Game Config Screen** â€” Full Cloth Config + Mod Menu integration for all settings

### ðŸ› Fixes
- **Stuck on "Saving World" after restore countdown** â€” Replaced `stopServer()` with `halt(false)`; exits in ~1s instead of 28s lighting bug freeze
- **Empty inventory / no advancements after restore** â€” Added `playerList.saveAll()` to backup save phase so `.dat` files are flushed before compression
- **Player spawns at wrong position after restore** â€” Captures player XYZ via reflection, patches `level.dat` spawn coordinates (`Data.spawn.pos` IntArray) before world reloads
- **Auto timer backup not running** â€” Config now loads from `mochi.json5`; logs show `Loaded config from mochi.json, backupInterval=30` so you can verify
- **Backups saving to wrong folder** â€” Path resolves relative to game profile root (walks up from world dir looking for `config/`), not JVM working directory
- **Timer stops working after restore** â€” Executor preserved across restores with auto-recovery fallback
- **`backupBeforeRestore` toggle doing nothing** â€” Now calls `ExecutableBackup.call()` with `before_restore` comment before starting restore countdown
- **NullPointerException in Cleanup** â€” Null-safe `ctx` check for shutdown-triggered backups
- **Double `[Mochi] Mochi:` chat prefix** â€” Removed redundant prefix; all messages now show `[Mochi]` once consistently

### ðŸ› ï¸ Technical
- Dual-trigger shutdown: `SERVER_STOPPING` (primary) + `END_SERVER_TICK` (fallback)
- One-shot flag pattern prevents duplicate shutdown backup triggers
- AwaitThread simplified: `(int delay, Runnable task)` only
- Session closed before world delete to release file handles
- NBT reads use `NbtAccounter.unlimitedHeap()` for large worlds
- MC 26.3 NBT compat: `Pos` ListTag rebuilt (not `set()`), spawn `pos` IntArray, `BlockPos.containing()`, `ResourceKey.toString()`

### âš™ï¸ Compatibility
- Minecraft: 26.3
- Mod Loader: Fabric 0.19.5
- Java Version: 25
- Dependencies: Fabric API 0.161.0+26.3, Mod Menu (optional), Cloth Config (optional)

---

## ðŸ¡ v2.5.0 â€” Player Data & Prefix Fix
Fixed empty inventories and double-prefix spam after restores.

### ðŸ› Fixes
- Fixed **empty inventory after restore** â€” Backup now includes player `.dat` data
- Fixed **double `[Mochi] Mochi:` chat prefix** â€” Clean single `[Mochi]` prefix everywhere
- Fixed **wrong player data path** â€” Changed from `data/<UUID>.dat` to `players/data/<UUID>.dat`
- Fixed **NBT build errors** â€” MC 26.3 API compat: `getValue()` â†’ `.toString()`, `getYaw()` â†’ `getYRot()`

---

## ðŸ¡ v2.0.0 â€” Restore Freeze Fix
Rewrote restore system to prevent the 28-second server freeze on world restore.

### ðŸ› Fixes
- Fixed **"Stuck on Saving World" freeze** â€” Uses `halt(false)` instead of `stopServer()`; server halts in ~1 second
- Fixed **black screen with unclickable buttons** after restore â€” No longer touches client screens
- Fixed **chunk relocation errors** â€” Old chunk positions auto-fixed by Minecraft on world load
- Fixed **NPE in RestoreAsyncCommand** when no backups exist
- Fixed **config path** â€” Now writes to correct `config/mochi.json` location

### ðŸ› ï¸ Technical
- Server halt via reflection: `getDeclaredMethod("halt", boolean.class).invoke(server, false)`
- Wait for server thread via `FutureTask<>()` + `getRunningThread().join()`
- Session closed before deleting old world to release file handles
- Matches TextileBackup approach (decompiled reference from textile_backup-3.1.3-1.21.jar)

---

## ðŸ¡ v1.5.0 â€” Discord Notifications
Added Discord webhook support for event notifications.

### âœ¨ Features
- **Discord Webhooks** â€” Send rich embed notifications on backup success/failure and world restore
- Config options: `discordEnabled`, `discordWebhookUrl`, `discordSendBackupStart/Done/Failed`, `discordSendRestore`

---

## ðŸ¡ v1.0.0 â€” Initial Release
Welcome to the very first release of Mochi! This mod was built to provide a simple, reliable, and cute backup solution for Fabric servers and singleplayer worlds. Thank you for trying it out!

### âœ¨ Features
- **Automatic Backups**: Schedule backups at custom intervals (default: every hour).
- **Manual Commands**: Instantly create, list, restore, and delete backups using `/mochi` commands.
- **In-Game Config Screen**: Full Mod Menu and Cloth Config integration. You can now change your backup settings (interval, compression, limits) directly in-game without touching a single JSON file!
- **Smart Cleanup**: Automatically deletes old backups based on count, age, or total file size to save your hard drive.
- **Safety First**: The mod automatically creates a "pre-restore" backup before rolling back your world, ensuring you never accidentally lose progress.
- **Server-Side Ready**: Multiplayer clients do not need to install Mochi to join a server running it.

### ðŸ› ï¸ Fixes & Technical Improvements
- Fixed "Invalid player data" error: We discovered that holding file streams open while zipping the playerdata folder could lock the files and corrupt player inventories. The backup manager now collects all files before creating the ZIP, completely preventing this issue.
- Command Permissions: Commands are strictly restricted to Operators (OP / Game Master) to prevent unauthorized players from deleting backups or reverting the server world.
- Minecraft 26.3 Support: Fully built on the new Mojang Official Mappings. No more intermediary/Yarn mapping conflicts!

### âš™ï¸ Compatibility
- Minecraft: 26.1, 26.2, 26.3
- Mod Loader: Fabric
- Java Version: 25
- Dependencies: Fabric API (Required), Mod Menu + Cloth Config (Optional, for in-game config)

### ðŸ‘¤ Credits
- Developer: LeafSpeak

