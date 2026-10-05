# Mochi Backup — Changelog

---

## 🍡 v3.0.0 — Full Restore Fix & Config Overhaul
All critical restore bugs are fixed. Timer backups, config loading, and player data now work perfectly.

### ✨ Features
- **Discord Webhooks** — Rich embed notifications on backup start/done/failed and world restore events
- **JSON5 Config Support** — Edit `mochi.json5` in-game; mod auto-converts to `mochi.json` on startup
- **Safety Backup Before Restore** — Automatic backup of current world before every `/mochi restore`
- **In-Game Config Screen** — Full Cloth Config + Mod Menu integration for all settings

### 🐛 Fixes
- **Stuck on "Saving World" after restore countdown** — Replaced `stopServer()` with `halt(false)`; exits in ~1s instead of 28s lighting bug freeze
- **Empty inventory / no advancements after restore** — Added `playerList.saveAll()` to backup save phase so `.dat` files are flushed before compression
- **Player spawns at wrong position after restore** — Captures player XYZ via reflection, patches `level.dat` spawn coordinates (`Data.spawn.pos` IntArray) before world reloads
- **Auto timer backup not running** — Config now loads from `mochi.json5`; logs show `Loaded config from mochi.json, backupInterval=30` so you can verify
- **Backups saving to wrong folder** — Path resolves relative to game profile root (walks up from world dir looking for `config/`), not JVM working directory
- **Timer stops working after restore** — Executor preserved across restores with auto-recovery fallback
- **`backupBeforeRestore` toggle doing nothing** — Now calls `ExecutableBackup.call()` with `before_restore` comment before starting restore countdown
- **NullPointerException in Cleanup** — Null-safe `ctx` check for shutdown-triggered backups
- **Double `[Mochi] Mochi:` chat prefix** — Removed redundant prefix; all messages now show `[Mochi]` once consistently

### 🛠️ Technical
- Dual-trigger shutdown: `SERVER_STOPPING` (primary) + `END_SERVER_TICK` (fallback)
- One-shot flag pattern prevents duplicate shutdown backup triggers
- AwaitThread simplified: `(int delay, Runnable task)` only
- Session closed before world delete to release file handles
- NBT reads use `NbtAccounter.unlimitedHeap()` for large worlds
- MC 26.3 NBT compat: `Pos` ListTag rebuilt (not `set()`), spawn `pos` IntArray, `BlockPos.containing()`, `ResourceKey.toString()`

### ⚙️ Compatibility
- Minecraft: 26.3
- Mod Loader: Fabric 0.19.5
- Java Version: 25
- Dependencies: Fabric API 0.161.0+26.3, Mod Menu (optional), Cloth Config (optional)

---

## 🍡 v2.5.0 — Player Data & Prefix Fix
Fixed empty inventories and double-prefix spam after restores.

### 🐛 Fixes
- Fixed **empty inventory after restore** — Backup now includes player `.dat` data
- Fixed **double `[Mochi] Mochi:` chat prefix** — Clean single `[Mochi]` prefix everywhere
- Fixed **wrong player data path** — Changed from `data/<UUID>.dat` to `players/data/<UUID>.dat`
- Fixed **NBT build errors** — MC 26.3 API compat: `getValue()` → `.toString()`, `getYaw()` → `getYRot()`

---

## 🍡 v2.0.0 — Restore Freeze Fix
Rewrote restore system to prevent the 28-second server freeze on world restore.

### 🐛 Fixes
- Fixed **"Stuck on Saving World" freeze** — Uses `halt(false)` instead of `stopServer()`; server halts in ~1 second
- Fixed **black screen with unclickable buttons** after restore — No longer touches client screens
- Fixed **chunk relocation errors** — Old chunk positions auto-fixed by Minecraft on world load
- Fixed **NPE in RestoreAsyncCommand** when no backups exist
- Fixed **config path** — Now writes to correct `config/mochi.json` location

### 🛠️ Technical
- Server halt via reflection: `getDeclaredMethod("halt", boolean.class).invoke(server, false)`
- Wait for server thread via `FutureTask<>()` + `getRunningThread().join()`
- Session closed before deleting old world to release file handles
- Matches TextileBackup approach (decompiled reference from textile_backup-3.1.3-1.21.jar)

---

## 🍡 v1.5.0 — Discord Notifications
Added Discord webhook support for event notifications.

### ✨ Features
- **Discord Webhooks** — Send rich embed notifications on backup success/failure and world restore
- Config options: `discordEnabled`, `discordWebhookUrl`, `discordSendBackupStart/Done/Failed`, `discordSendRestore`

---

## 🍡 v1.0.0 — Initial Release
Welcome to the very first release of Mochi! This mod was built to provide a simple, reliable, and cute backup solution for Fabric servers and singleplayer worlds. Thank you for trying it out!

### ✨ Features
- **Automatic Backups**: Schedule backups at custom intervals (default: every hour).
- **Manual Commands**: Instantly create, list, restore, and delete backups using `/mochi` commands.
- **In-Game Config Screen**: Full Mod Menu and Cloth Config integration. You can now change your backup settings (interval, compression, limits) directly in-game without touching a single JSON file!
- **Smart Cleanup**: Automatically deletes old backups based on count, age, or total file size to save your hard drive.
- **Safety First**: The mod automatically creates a "pre-restore" backup before rolling back your world, ensuring you never accidentally lose progress.
- **Server-Side Ready**: Multiplayer clients do not need to install Mochi to join a server running it.

### 🛠️ Fixes & Technical Improvements
- Fixed "Invalid player data" error: We discovered that holding file streams open while zipping the playerdata folder could lock the files and corrupt player inventories. The backup manager now collects all files before creating the ZIP, completely preventing this issue.
- Command Permissions: Commands are strictly restricted to Operators (OP / Game Master) to prevent unauthorized players from deleting backups or reverting the server world.
- Minecraft 26.3 Support: Fully built on the new Mojang Official Mappings. No more intermediary/Yarn mapping conflicts!

### ⚙️ Compatibility
- Minecraft: 26.1, 26.2, 26.3
- Mod Loader: Fabric
- Java Version: 25
- Dependencies: Fabric API (Required), Mod Menu + Cloth Config (Optional, for in-game config)

### 👤 Credits
- Developer: LeafSpeak
