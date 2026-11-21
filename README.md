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
- 10-second countdown (configurable), then a clean server shutdown
- Automatic rollback if a restore fails partway through — your original world stays intact

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

---

## Commands

All commands use `/mochi`. Works with or without cheats in singleplayer.

| Command | Description |
| :--- | :--- |
| `/mochi start [comment]` | Create a backup immediately |
| `/mochi list` | List backups with clickable **[Restore]** / **[Delete]** buttons |
| `/mochi restore <filename>` | Restore a backup (countdown, then world files are swapped) |
| `/mochi delete <filename>` | Delete a specific backup |
| `/mochi cleanup` | Run retention rules manually |

After `/mochi list`, simply click the buttons in chat — no need to type long filenames.

---

## How Restore Works

1. Run `/mochi restore <filename>`
2. A 10-second countdown appears in chat
3. The server halts cleanly and releases the world file lock
4. The current world is renamed and kept as a backup
5. The restored world is moved into place
6. The old world is deleted after verification
7. Rejoin — your restored world is ready

If anything goes wrong mid-restore, Mochi automatically rolls back to the original world.

---

## Dependencies

| Mod | Required | Purpose |
| :--- | :---: | :--- |
| Fabric API | Yes | Core Fabric functionality |
| Mod Menu | Recommended | In-game config access |
| Cloth Config API | Recommended | Config screen UI |

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
