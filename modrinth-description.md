# 🍡 Mochi Backup — v3.1.0

A simple, reliable Minecraft backup mod for Fabric. Back up your world automatically on interval, manually with `/mochi start`, or restore anytime with `/mochi restore latest`.

## What It Does

- **Auto-backups** on a timer (configurable interval, default 1 hour)
- **Shutdown backups** — saves a backup when you save and quit
- **Instant restore** — `/mochi restore latest` reverts your world to any previous backup
- **Cleanup** — auto-deletes old backups by count, age, or total size
- **Discord webhooks** — optional rich embed notifications for every event

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/mochi start [comment]` | 4 | Create a manual backup |
| `/mochi restore latest` | 4 | Restore the most recent backup |
| `/mochi restore <timestamp> [comment]` | 4 | Restore a specific backup by timestamp |
| `/mochi list` | 4 | List all available backups |
| `/mochi delete <timestamp>` | 4 | Delete a specific backup |
| `/mochi cleanup` | 4 | Run cleanup (delete old backups) |
| `/mochi killR` | 4 | Cancel a restore that is counting down |
| `/mochi whitelist` | 4 | Manage player whitelist |
| `/mochi blacklist` | 4 | Manage player blacklist |

In singleplayer the owner can always run these commands, with or without cheats.

## In-Game Config

Open the config screen via **Options > Controls > Mochi Settings** (requires Mod Menu).

| Setting | Default | Description |
|---------|---------|-------------|
| Interval (seconds) | 3600 | Auto-backup timer. Set to 0 to disable. |
| Restore Delay | 30 | Seconds of countdown before world replaces |
| Backup on Shutdown | true | Create backup when the server stops (singleplayer / integrated server) |
| Backup Before Restore | true | Safety backup before every restore |
| Delete After Restore | true | Remove backup file after restoring |
| Backups to Keep | 10 | Max backups per world (0 = unlimited) |
| Compression Level | 7 | 0 = fastest, 9 = smallest |
| Archive Format | ZIP | ZIP, GZIP, or TAR |
| File Blacklist | [] | Files to skip (e.g., `session.lock`) |

## Discord Webhooks

Set these in config to receive notifications:

```json5
{
  "discordEnabled": true,
  "discordWebhookUrl": "https://discord.com/api/webhooks/...",
  "discordSendBackupStart": true,
  "discordSendBackupDone": true,
  "discordSendBackupFailed": true,
  "discordSendRestore": true
}
```

## Compatibility

- **Minecraft**: 26.1 – 26.3
- **Fabric Loader**: 0.19.5
- **Fabric API**: 0.160.5+26.3
- **Cloth Config**: required
- **Java**: 25
- Singleplayer and multiplayer ready (server-side only, clients don't need the mod)

## How It Works

```
Backup:  saveAllChunks() → compress entire world folder → write to .zip
Restore: safety backup → halt(false) → decompress to temp → integrity check
         → rename old world aside → move new world in → verify → delete old world
```

Uses `halt(false)` (not `stopServer()`) to avoid Minecraft's lighting bug that causes 28-second freezes. The old world is only deleted once the restored world has been verified, and is put back automatically if anything fails. Player position and inventory are preserved exactly as they were at backup time.

## Credits

Developer: **LeafSpeak**

Built with ❤️ using Fabric API, Cloth Config, and Mod Menu.
