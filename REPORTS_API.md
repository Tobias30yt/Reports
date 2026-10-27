# Reports 1.3.0 — Tidalwave API and languages

This extends the existing Reports plugin. Existing reports, sanctions, Discord integration,
GrimAC integration and Snake remain in place. Back up `plugins/Reports/` before upgrading.
Use the existing Paper 26.3-compatible server and Java 25 or newer.

## Install

1. Stop the Minecraft server cleanly and replace its existing Reports JAR with `Reports.jar`.
2. Keep the existing `plugins/Reports/config.yml` and SQLite database.
3. Sign in to Tidalwave as the main administrator and open `/admin/api`.
4. For the already registered Reports server, download a rotated `api.yml`, or register
   another Minecraft server. Put the file at `plugins/Reports/api.yml`.
5. Start the server. API synchronization runs in a separate worker every ten seconds.

The private key is generated per server and is never embedded in the JAR or source code.
Rotating the key invalidates the previous credential. Disabling the client in the website
stops further writes. Do not commit the configured `api.yml` or share it with players.
Keep the same API registration when updating an existing Minecraft server.

SQLite remains the local enforcement store, so bans and mutes continue to work during an
internet outage. SQLite triggers queue every report/sanction change in the same transaction.
The website acknowledges ordered batches; only acknowledged events leave the queue.
Existing records are queued on first upgrade. The website database is a server-scoped replica;
this version does not merge sanctions between separate Minecraft servers or restore a lost
local database automatically. Back up both databases. If the local database is restored to
an older sequence, register a new API client and re-seed the replica before using it.

## Languages

On first use after upgrade (or first join), players choose English or German in a GUI.
`/language` reopens it. Selection is stored in player data. With LuckPerms present, the
plugin also sets `language.englisch` and `language.german` to mutually exclusive booleans.
No LuckPerms administrative permission is given to players; only those two fixed nodes
for the selecting player's UUID are changed by the plugin.

Minecraft menus and messages use the bilingual `translations.tsv` catalogue. Existing
custom messages can add matching translation pairs. Player-written reasons, report text,
usernames, anticheat details and Discord messages are not machine translated.
Mute feedback shows the stored reason and remaining duration; timed mutes expire without
requiring a reconnect. Console diagnostics and Discord staff output remain operational English/German
as configured independently of individual Minecraft players.

## Validation

`mvn package` builds the shaded JAR and embeds its integrity manifest.
`java -cp target/Reports.jar tests/OutboxCheck.java` tests durable sync, acknowledgments,
restart recovery, escaping, removals and expiry using a temporary SQLite database.
`java -cp target/Reports.jar de.example.reports.integrity.IntegrityManifest verify target/Reports.jar`
checks the published artifact. The SQLite driver is kept in its original namespace because
its native library cannot safely be relocated.

The language GUI and LuckPerms node changes should also be checked in-game on your server
after installation; a successful build alone does not verify those interactions.
