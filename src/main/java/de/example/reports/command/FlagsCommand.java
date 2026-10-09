package de.example.reports.command;

import de.example.reports.database.Database;
import de.example.reports.grim.GrimHook;
import de.example.reports.model.GrimFlag;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FlagsCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final Database database;
    private final GrimHook grim;

    public FlagsCommand(JavaPlugin plugin, Database database, GrimHook grim) {
        this.plugin = plugin;
        this.database = database;
        this.grim = grim;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("reports.flags")) {
            sender.sendMessage("§cDafür hast du keine Berechtigung.");
            return true;
        }
        if (args.length != 2 || !(args[0].equalsIgnoreCase("see") || args[0].equalsIgnoreCase("remove"))) {
            sender.sendMessage("§cBenutzung: /flags <see|remove> <spieler>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        database.playerByName(args[1]).thenAccept(found ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (found.isEmpty()) {
                        sender.sendMessage("§cSpieler nicht bekannt. Der Spieler muss dem Server mindestens einmal beigetreten sein.");
                        return;
                    }
                    var target = found.get();
                    if (action.equals("remove")) {
                        grim.clearLatestFlags(target.uuid());
                        remove(sender, target.uuid(), target.name());
                    } else {
                        show(sender, target.uuid(), target.name());
                    }
                })
        ).exceptionally(error -> {
            plugin.getLogger().severe("Could not look up GrimAC flags: " + error.getMessage());
            sender.sendMessage("§cDie GrimAC-Flags konnten nicht geladen werden.");
            return null;
        });
        return true;
    }

    private void show(CommandSender sender, java.util.UUID playerUuid, String playerName) {
        database.grimFlags(playerUuid, 50).thenAccept(flags ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§6GrimAC-Flags für §e" + playerName + "§6 (neueste 50):");
                    if (flags.isEmpty()) {
                        sender.sendMessage(grim.available()
                                ? "§7Keine seit Aktivierung der Aufzeichnung gespeicherten Flags."
                                : "§cGrimAC-Flag-Aufzeichnung ist nicht verfügbar.");
                        return;
                    }
                    for (GrimFlag flag : flags) {
                        sender.sendMessage("§8[" + TIMESTAMP.format(flag.timestamp()) + "] §c"
                                + flag.checkName() + " §7" + flag.details());
                    }
                })
        ).exceptionally(error -> {
            plugin.getLogger().severe("Could not load GrimAC flags for " + playerName + ": " + error.getMessage());
            sender.sendMessage("§cDie GrimAC-Flags konnten nicht geladen werden.");
            return null;
        });
    }

    private void remove(CommandSender sender, java.util.UUID playerUuid, String playerName) {
        database.removeGrimFlags(playerUuid).thenAccept(removed ->
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§a" + removed + " gespeicherte GrimAC-Flags von "
                                + playerName + " wurden gelöscht. GrimAC selbst wurde nicht zurückgesetzt.")
                )
        ).exceptionally(error -> {
            plugin.getLogger().severe("Could not remove GrimAC flags for " + playerName + ": " + error.getMessage());
            sender.sendMessage("§cDie gespeicherten GrimAC-Flags konnten nicht gelöscht werden.");
            return null;
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("reports.flags")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("see", "remove").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
