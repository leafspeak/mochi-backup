# 🍡 Mochi Backup — v3.0.0

A simple, reliable Minecraft backup mod for Fabric. Back up your world automatically on interval, manually with `/mochi backup`, or restore anytime with `/mochi restore latest`.

## What It Does

- **Auto-backups** on a timer (configurable interval, default 1 hour)
- **Shutdown backups** — saves a backup when you save and quit
- **Instant restore** — `/mochi restore latest` reverts your world to any previous backup
- **Cleanup** — auto-deletes old backups by count, age, or total size
- **Discord webhooks** — optional rich embed notifications for every event

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/mochi backup [comment]` | 4 | Create a manual backup |
| `/mochi restore latest` | 4 | Restore the most recent backup |
| `/mochi restore <timestamp>` | 4 | Restore a specific backup by filename |
| `/mochi list` | 4 | List all available backups |
| `/mochi cleanup` | 4 | Run cleanup (delete old backups) |
| `/mochi whitelist` | 4 | Manage player whitelist |
| `/mochi blacklist` | 4 | Manage player blacklist |

## In-Game Config

Open the config screen via **Options > Controls > Mochi Settings** (requires Mod Menu).

| Setting | Default | Description |
|---------|---------|-------------|
| Interval (seconds) | 3600 | Auto-backup timer. Set to 0 to disable. |
| Restore Delay | 30 | Seconds of countdown before world replaces |
| Backup on Shutdown | true | Create backup when server stops |
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

- **Minecraft**: 26.3
- **Fabric Loader**: 0.19.5
- **Fabric API**: 0.161.0+26.3
- **Java**: 25
- Singleplayer and multiplayer ready (server-side only, clients don't need the mod)

## How It Works

```
Backup:  saveAllChunks() → compress entire world folder → write to .zip
Restore: halt(false) → wait for server → delete old world → unzip backup → rejoin
```

Uses `halt(false)` (not `stopServer()`) to avoid Minecraft's lighting bug that causes 28-second freezes. Player position and inventory are preserved exactly as they were at backup time.

## Credits

Developer: **LeafSpeak**

Built with ❤️ using Fabric API, Cloth Config, and Mod Menu.
