# 🍡 Mochi

**A lightweight, reliable backup mod for Fabric.**

Mochi runs quietly in the background and keeps your worlds safe. No complicated setup — just backups that work when you need them.

Whether you're protecting against corruption, testing datapacks, or just want a safety net before a big build, Mochi has you covered.

---

## Features

### Backups
- Automatic backups on a configurable timer
- Manual backups with a single command
- Auto-backup when you leave a world
- Separate backup folder per world
- Multithreaded compression for speed
- Choose between ZIP (faster) or GZIP (smaller files)

### Restore
- Restore that actually works — built on the proven `halt()` + `session.close()` method
- Player data preserved: inventory, advancements, stats, and position
- Safety backup created before every restore, so you can always roll back
- Configurable countdown (30 seconds by default), then a clean server shutdown
- The backup is extracted and integrity-checked **before** the world is touched
- The current world is renamed aside, not deleted, and is restored automatically if anything fails

### Retention
- Keep a set number of backups per world
- Auto-delete backups older than a chosen age
- Cap the total size of the backup folder
- Blacklist specific files from being backed up

### Discord Notifications (Optional)
- Rich embed webhooks with color-coded status messages
- Fires on backup start, backup complete, backup failed, and restore
- Disabled by default — enable only if you want it

### Configuration
- In-game config screen via Mod Menu and Cloth Config
- JSON5 config with comments, auto-converted from older formats
- No manual file editing required

---

## Installation

- **Singleplayer:** Install Mochi on the client — that's it.
- **Multiplayer:** Install Mochi on the server. Clients do not need the mod.

Backups are stored in `mochi_backups/<world-name>/` by default. You can change this in the config screen.

> **Note:** Restore requires a full restart of your client (singleplayer) or server (multiplayer) so world files can be swapped safely.
>
> **Note:** the backup-on-shutdown feature currently only triggers in singleplayer / integrated servers. On a dedicated server, use the timer or `/mochi start`.

---

## Commands

All commands use `/mochi`. Works with or without cheats in singleplayer.

| Command | Description |
| :--- | :--- |
| `/mochi start [comment]` | Create a backup immediately |
| `/mochi list` | List backups with their timestamps (tab-completion works after `/mochi restore`) |
| `/mochi restore <filename>` | Restore a backup (countdown, then world files are swapped) |
| `/mochi delete <filename>` | Delete a specific backup |
| `/mochi cleanup` | Run retention rules manually |

Backup names are tab-completed — no need to type a long filename by hand.

---

## How Restore Works

1. Run `/mochi restore <timestamp> [comment]`
2. A countdown appears in chat (30 seconds by default)
3. A safety backup of the current world is created
4. The server halts cleanly and releases the world file lock
5. The backup is extracted into a temporary folder and its integrity is checked
6. The current world is **renamed aside** — it is not deleted yet
7. The restored world is moved into place and verified
8. Only then is the old world removed; rejoin and your world is ready

If anything fails — a corrupt archive, a failed move, a missing `level.dat` — Mochi puts the
original world back and leaves it untouched. Nothing is deleted before the replacement has been
verified.

> In **Strict** integrity mode (the default) a backup that fails its hash check is not restored.
> Set **Integrity Mode** to *Permissible* if you deliberately want to restore a damaged archive.

---

## Dependencies

| Mod | Required | Purpose |
| :--- | :---: | :--- |
| Fabric API | Yes | Core Fabric functionality |
| Mod Menu | Recommended | In-game config access |
| Cloth Config API | Yes | Config screen and config serialisation |

---

## Compatibility

- **Minecraft:** 26.1 – 26.3
- **Loader:** Fabric
- **Java:** 25
- **Environment:** Client and server

---

## Credits

- **Developer:** LeafSpeak
- **Inspired by:** Textile Backup (restore architecture)
- **Icon:** Custom mochi illustration

---

## License

MIT

---

*Made with 🍡 by LeafSpeak.*
