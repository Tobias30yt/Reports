# Reports for Paper 26.3

Production-oriented player-report plugin for **Paper 26.3 / Java 25**. Reports are persisted in SQLite and can be relayed to Discord through JDA.

## Build and installation

Install JDK 25 and Maven, then run:

```powershell
mvn clean package
```

Copy `target/Reports.jar` to the server's `plugins/` directory and start Paper once. Each package build regenerates `moderation/integrity.sha256` and embeds the SHA-256 manifest in the JAR. On startup, the plugin verifies every JAR file entry against that manifest and disables itself if an entry was changed, removed, or added. The manifest cannot hash itself; it is the reference list for the other JAR entries. The plugin creates `plugins/Reports/config.yml` and `reports.db` automatically. Use a current Paper 26.3 build; the Paper API dependency follows Paper's 26.3 build-version range.

## Commands and permissions

| Command | Permission | Purpose |
|---|---|---|
| `/report` | `reports.create` (default: everyone) | Open the player selection UI |
| `/reports info <id>` | `reports.view` | Show report data |
| `/reports status <id> <OPEN|REVIEWING|RESOLVED|REJECTED>` | `reports.manage` | Change report status |
| `/reports reload` | `reports.admin` | Reload configuration and Discord connection |

## Discord bot setup

The Discord integration is optional. Reports are always written to SQLite, even if Discord is disabled or temporarily unavailable.

### 1. Create the application and bot

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and choose **New Application**.
2. Give the application a recognizable name, such as `Server Reports`, then select **Create**.
3. Open **Bot** in the left navigation and choose **Add Bot**.
4. Optionally set a username and avatar. The bot does **not** need the privileged **Message Content Intent**, **Server Members Intent**, or **Presence Intent** for this plugin.

### 2. Copy the bot token securely

In **Bot**, use **Reset Token** or **Copy Token** to obtain the token. Treat it like a password: anyone holding it can control the bot. Never paste it into public messages, screenshots, Git repositories, or your `README`.

Store it only on the server in `plugins/Reports/config.yml`. If the token is ever exposed, immediately reset it in the Developer Portal and update the server configuration.

### 3. Invite the bot

1. In the Developer Portal, select **OAuth2** -> **URL Generator**.
2. Under **Scopes**, enable `bot`. This OAuth2 invite URL is how the bot is added to the server.
3. Under **Bot Permissions**, enable these minimum permissions:

   - **View Channels**
   - **Send Messages**
   - **Embed Links**
   - **Read Message History**

   For the chat archive, enable **Message Content Intent** under **Bot** -> **Privileged Gateway Intents**. Do not give the bot Administrator; channel permission overrides are the safer way to restrict its access.

4. Copy the generated URL, open it in a browser, select the Discord server, and authorize the bot.

For stricter access, do not grant broad server-wide administrator permissions. The bot needs **View Channels** in every channel that should be archived, but **Send Messages** and **Embed Links** can be limited to the report and archive channels through channel overrides.

### 4. Configure report and chat channels in Discord

A Discord member with **Manage Server** can configure the bot without copying IDs:

| Command | Purpose |
|---|---|
| `!setreportsgroup #reports` | Sets the channel for new report embeds. |
| `!setchatlogs #chat-logs` | Sets the archive channel; messages from the other text channels are copied there and retained in SQLite. |
| `!listchat` | Shows the newest archived messages. |
| `!listchat commands` | Shows archived `!` commands. |
| `!listchat player <name>` | Filters by Discord player name. |
| `!listchat message <text>` | Searches chat-message text. |
| `!chathelp` | Shows a command reminder. |

The archive channel is not copied back into itself, and bot messages are ignored to prevent loops. `!listchat` searches the SQLite archive, so it remains usable after messages are removed from the visible archive channel.

### 5. Enable Developer Mode and copy IDs

In Discord, open **User Settings** -> **Advanced** and enable **Developer Mode**. Then right-click these items and select **Copy ID**:

| Value | Where to copy it |
|---|---|
| `guild-id` | The Discord server icon |
| `report-channel-id` | The text channel for new report embeds |
| `staff-role-id` | The staff role in Server Settings -> Roles |

Use numeric IDs, not names, mentions, or channel links.

### 6. Configure the plugin
23nfig.yml`, stop the server, then configure this section:

```yaml
discord:
  enabled: true
  token: "PUT_THE_BOT_TOKEN_HERE"
  guild-id: "123456789012345678"
  report-channel-id: "123456789012345678"
  chat-log-channel-id: ""
  staff-role-id: "123456789012345678"
  retry-attempts: 3
```

Restart the server, or run `/reports reload` as a player with `reports.admin`. The plugin never writes the token to logs.

### 6. Verify the connection

1. Confirm that the bot appears online in the Discord server.
2. Submit a Minecraft report through `/report`.
3. Verify that a **New Report** embed arrives in the configured channel.
4. Click **View Other Reports** with an account holding the configured staff role.

The button returns the 25 most recent reports for that reported player privately to the staff member. It is deliberately fail-closed: if `staff-role-id` is blank or does not match one of the clicker's roles, report details are denied.

### Discord troubleshooting

| Symptom | Check |
|---|---|
| Bot is offline | Ensure `enabled: true`, use a current token, then restart or run `/reports reload`. |
| No embed arrives | Verify all three IDs, the bot's channel permissions, and that the report channel is a normal text channel. |
| `View Other Reports` is denied | Copy the staff role's numeric ID again; users must actually hold that role. |
| Token was leaked | Reset it in the Developer Portal immediately, replace it in `config.yml`, then reload. |
| Discord is down | The report remains safely stored in SQLite; delivery is retried according to `retry-attempts`. |

## Player UI

`/report` presents known players, not just online players. A join updates the UUID-keyed `known_players` table; that prevents duplicate display entries. The 7-row UI uses 45 player slots, with safe pagination, a cycling all/online/offline hopper filter, and a native Paper virtual sign prompt for case-insensitive offline-player search. Selecting a player opens another four-line sign prompt; nonempty text is joined and checked against `reports.max-reason-length`.

After a report is saved, players receive a clickable chat link to open Snake (or can use `/snake`). Control the snake by clicking the on-screen arrows, arranged like an arrow-key cluster (up centered above left, down, and right); it moves one space per second (20 ticks). Hitting a wall or the snake ends the round, and the game-over screen offers a restart or exit. The game protects the player from damage only while it is open and restores their previous invulnerability state when it ends.

Combat logging is configurable under `reports.waiting-game`: set `combat-tag-seconds` to choose the PvP tag duration, then enable `combat-logging` and set its console `command`. The default is disabled. `%player%` and `%uuid%` are replaced with the player who disconnects during the tag. Both PvP participants are tagged, including a player shooting a projectile at another player.

On startup, the plugin disables Paper's `unsupported-settings.update-equipment-on-player-actions` option for the running server to preserve vanilla-style rapid attribute/cooldown swapping. If the Paper runtime does not expose that setting, the plugin logs the manual `config/paper-global.yml` setting to apply (`false`).

Inventory interactions are cancelled for plugin-held report and Snake inventories. The report-selection and Snake screens use typed holders rather than title comparisons.

## Database

SQLite is used by default at `plugins/Reports/reports.db`, with WAL mode and one Hikari connection (SQLite's appropriate write model). Database work is serialized on a dedicated executor, never on the Minecraft main thread. Tables `known_players` and `reports` are created automatically. Report IDs are SQLite `INTEGER PRIMARY KEY AUTOINCREMENT` values and survive restarts.

## GrimAC

GrimAC is a soft dependency. Its presence is detected safely and a report records whether it is available. Current public Grim APIs do not expose a stable, generic snapshot API for per-player flags/violations, so this project intentionally does **not** link to or invent an unstable Grim API. To add actual flag values, implement `GrimHook#flags(Player)` against the exact Grim version/API deployed on the server; reports remain functional with no Grim installation.

## Troubleshooting

- **Plugin disables on startup:** verify Java 25 and write access to `plugins/Reports`.
- **Discord is not sending:** leave `discord.enabled: false` until all IDs and token are set. Database reports still work when Discord is unavailable.
- **Button says unauthorized:** set the numeric `discord.staff-role-id`; an empty role is intentionally fail-closed.
- **No historical players:** only players who have joined since this plugin was installed are known. Existing Paper offline-player files are intentionally not bulk-imported because their historical name data is not a trustworthy player index.
