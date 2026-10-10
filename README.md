# Reports

Reports is a staff and player-support plugin for **Paper 26.3** and **Java 25**. It combines player reports, an in-game staff workflow, persistent moderation, optional Discord integration, a Discord chat archive, GrimAC flag history, and a small Snake game.

## Features

- **Player reports:** Players can report online or previously-seen players using a paginated inventory, online/offline filters, player search, and a prompted reason. Reports receive persistent IDs and survive restarts.
- **Staff report management:** Browse reports in an in-game GUI, filter by status, inspect report details, and update statuses. The GUI also offers moderation actions directly from a report.
- **Persistent moderation:** Permanent and timed bans/mutes are stored in SQLite, enforced when a player joins, and applied immediately to online players. Unban removes both the Reports ban and mute.
- **GrimAC flag history:** When GrimAC is installed, Reports listens for its public Bukkit flag event and stores captured check names and details. Staff can inspect or remove the history Reports has stored.
- **Optional Discord bot:** Forward new reports as embeds, review reports and moderate through staff-only slash commands, and show related reports from a report embed.
- **Discord chat archive:** Optionally copy Discord text-channel messages to an archive channel and search the SQLite archive, including filters for commands, author, and message text.
- **Snake mini-game:** Players can start `/snake` or use the clickable chat link after making a report. Control it with the GUI arrow buttons; the snake advances once per second.
- **Snake leaderboard:** Reports saves each player's best score and displays the top ten with `/snake top` (or `/snake leaderboard`). Modded Snake scores are tied to a server-issued session and checked against elapsed play time.
- **Release update notices:** On startup and every 48 hours, Reports checks GitHub for a newer stable release. If one is available, the console and online operators receive the release page and direct JAR download links. Disable with `reports.update-checker.enabled: false`.
- **Combat logging:** Optionally prevent starting Snake during a PvP tag and execute a configured console command if a tagged player disconnects.
- **Paper attribute swapping:** On startup, Reports sets Paper's `unsupported-settings.update-equipment-on-player-actions` setting to `false` for vanilla-style attribute/cooldown swapping.
- **Startup integrity check:** The build embeds a SHA-256 manifest of JAR entries and writes a matching copy to `moderation/integrity.sha256`. Reports disables itself if the JAR contents do not match its embedded manifest.
- **SQLite storage:** Reports, player history, sanctions, GrimAC flags, and Discord chat logs are persisted locally. Discord is optional; reports still work without it.

## Download and installation

Download the plugin JAR from the [latest GitHub release](https://github.com/Tobias30yt/Reports/releases/latest). Install Java 25 and a current Paper 26.3 server, then place `Reports.jar` in the server's `plugins/` folder and restart the server. Reports creates `plugins/Reports/config.yml` and its SQLite database on first startup.

The optional [Claims Client Fabric mod](https://github.com/Tobias30yt/Claims-Client/releases) adds a vanilla-style Snake screen for players on supported servers. Without it, the chest-based game remains available.

GrimAC is optional and is only needed for Grim flag capture. No additional dependency plugin is needed for Reports' bundled libraries.

### Build from source

Install JDK 25 and Maven, clone this repository, and run:

```powershell
mvn clean package
```

The resulting plugin is `target/Reports.jar`. Packaging refreshes `moderation/integrity.sha256` and embeds the manifest in the JAR. The manifest does not hash itself, and this integrity check is not a digital signature: an attacker able to replace the JAR and its embedded manifest can replace both.

## Minecraft commands and permissions

| Command | Permission (default) | What it does |
|---|---|---|
| `/report` | `reports.create` (everyone) | Open the player-report UI. |
| `/snake` | none | Start Snake. |
| `/reports` | `reports.agui` (operators) | Open the staff report-management GUI. |
| `/reports info <id>` | `reports.view` (operators) | Show a report's stored details. |
| `/reports status <id> <OPEN\|REVIEWING\|RESOLVED\|REJECTED>` | `reports.manage` (operators) | Change a report's status. |
| `/reports reload` | `reports.admin` (operators) | Reload plugin settings and restart the Discord connection. |
| `/flags see <player>` | `reports.flags` (operators) | Show up to 50 newest GrimAC flag events stored by Reports. |
| `/flags remove <player>` | `reports.flags` (operators) | Delete the player's stored GrimAC flag history. |
| `/ban <player> [reason]` | `reports.ban` (operators) | Permanently ban a known player. |
| `/tempban <player> <duration> [reason]` | `reports.tempban` (operators) | Apply a timed ban; duration suffixes are `m`, `h`, and `d` (for example `30m`, `12h`, `7d`). |
| `/mute <player> [reason]` | `reports.mute` (operators) | Permanently mute a known player in chat. |
| `/tempmute <player> <duration> [reason]` | `reports.tempmute` (operators) | Apply a timed chat mute. |
| `/unban <player>` | `reports.unban` (operators) | Remove both the Reports ban and mute for a known player. |

The reports GUI has status filters, pagination, a report-details view, and permission-gated ban/mute/unban actions. Timed ban presets in the GUI are 1 hour, 1 day, 7 days, or 30 days; timed mute presets are 30 minutes, 6 hours, 1 day, or 7 days. Right-click the timed action to cycle its duration.

The `/flags` commands show and delete **Reports' own captured history**; removing entries does not reset GrimAC's current violation level. Capture begins once Reports registers its listener at startup. Players must have joined since Reports was installed to be searchable by player name.

## Configuration

The plugin writes defaults to `plugins/Reports/config.yml`. Main settings:

```yaml
database:
  file: reports.db
discord:
  enabled: false
  token: ""
  guild-id: ""
  report-channel-id: ""
  chat-log-channel-id: ""
  staff-role-id: ""
  retry-attempts: 3
reports:
  max-reason-length: 256
  page-size: 45
  waiting-game:
    enabled: true
    combat-tag-seconds: 15
    combat-logging:
      enabled: false
      command: "kick %player% Combat logging is not allowed."
timezone: "Europe/Berlin"
```

`%player%` and `%uuid%` in the combat-logging command are replaced with the player who disconnected during an active combat tag. Both PvP participants are tagged, including a player who hits another player with a projectile. The default combat-logging command is disabled.

Paper's attribute-swapping setting is changed for the running server on plugin startup. If the setting cannot be accessed on a particular Paper build, Reports logs the manual `config/paper-global.yml` setting (`unsupported-settings.update-equipment-on-player-actions: false`).

## Discord integration

Discord is disabled by default. To enable it:

1. Create a bot in the [Discord Developer Portal](https://discord.com/developers/applications) and copy its token. Keep the token private; put it only in the server's `plugins/Reports/config.yml`.
2. Invite it with the `bot` scope and the minimum channel permissions needed: **View Channels**, **Send Messages**, **Embed Links**, and **Read Message History**.
3. Enable **Message Content Intent** in the bot's privileged gateway intents if you want Discord chat archiving and `!` commands.
4. Set `discord.enabled: true` and `discord.token` in `config.yml`, then restart or run `/reports reload`.
5. In the Discord server, use these setup commands (the sender must have **Manage Server**):

   | Discord message | Purpose |
   |---|---|
   | `!setreportsgroup #reports` | Set the channel for new report embeds. |
   | `!setchatlogs #chat-logs` | Set the channel where messages are archived. |
   | `!chathelp` | Show the chat command reminder. |

   These commands save the guild and channel IDs in the plugin config. To enable staff-only slash commands, set `discord.staff-role-id` to the numeric ID of the staff role, or use a member with **Manage Server**.

The bot registers these guild slash commands when it connects to the configured `guild-id`:

| Slash command | Purpose |
|---|---|
| `/menu` | Open the private staff menu. |
| `/reports` | Show the 10 newest reports. |
| `/report id:<id>` | Show report details, including captured GrimAC flags. |
| `/flags see player:<name>` | View up to 10 recent stored GrimAC flag events, including check details. |
| `/flags remove player:<name>` | Delete the player's stored flag history. |
| `/ban`, `/tempban`, `/mute`, `/tempmute`, `/unban` | Apply or remove persistent Reports sanctions. |

All staff slash commands require **Manage Server** or the configured `discord.staff-role-id`. Removing GrimAC flags through Discord deletes only Reports' stored history; it does not reset GrimAC's violation level.

Staff can use `!listchat` to show recent archived messages, or filter with `!listchat commands`, `!listchat player <name>`, and `!listchat message <text>`. The newest 15 matching messages are returned. Messages from bots are ignored, and the archive channel is not copied into itself. Messages are retained in SQLite even if they are later removed from Discord.

New report embeds contain a **View Other Reports** button. Access to that button is restricted to members with the configured staff role; if no role ID is configured, the button fails closed. Discord outages do not prevent Minecraft reports from being saved locally.

## Data and integrity

The default database is `plugins/Reports/reports.db`. SQLite runs in WAL mode; database work is handled asynchronously. It stores known players, reports, sanctions, GrimAC flag history, and (when enabled) Discord chat logs.

At startup, Reports verifies that the JAR's entries match its embedded SHA-256 manifest. This detects JAR changes when the manifest is unchanged; it is an integrity check, not a cryptographic signature or protection against replacing both the JAR and manifest.

## Troubleshooting

- **Reports does not start:** Check that the server runs Java 25 and Paper 26.3, and that the plugin can access its data directory. The server log will state whether integrity verification passed.
- **A player is not listed:** The player must have joined since Reports was installed; only those player names are indexed.
- **No GrimAC events appear:** Confirm GrimAC is installed and enabled before Reports starts. `/flags` only displays events captured after Reports registered its listener; GrimAC's internal violation level is separate.
- **Discord commands or embeds are missing:** Check `discord.enabled`, token and configured channel, bot channel permissions, Message Content Intent for chat archiving, and the server log.
- **A Discord report button denies access:** Configure the numeric `discord.staff-role-id` and ensure the clicking member has that role.
- **The bot token was exposed:** Reset it in the Discord Developer Portal, update the config, and restart or reload the plugin.
- **A muted player's chat is not blocked:** Reports applies stored mutes when the player joins. Check that the player has the expected Reports sanction and that another plugin is not altering chat behavior.
# Optional Claims Client integration

Installing the optional [Claims Client Fabric mod](https://github.com/Tobias30yt/Claims-Client/releases) lets players use its vanilla-style Snake screen while waiting after a report. Without the mod, the existing chest-based Snake game remains available. The client joins the game only after Reports validates its plugin-channel handshake; closing the screen ends the matching server session.

