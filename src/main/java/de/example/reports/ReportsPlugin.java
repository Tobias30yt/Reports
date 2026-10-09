package de.example.reports;

import de.example.reports.command.FlagsCommand;
import de.example.reports.command.ModerationCommand;
import de.example.reports.command.ReportCommand;
import de.example.reports.command.ReportsCommand;
import de.example.reports.command.SnakeCommand;
import de.example.reports.config.PluginSettings;
import de.example.reports.database.Database;
import de.example.reports.discord.DiscordService;
import de.example.reports.grim.GrimHook;
import de.example.reports.gui.AdminReportGuiService;
import de.example.reports.gui.ReportGuiService;
import de.example.reports.gui.SignInput;
import de.example.reports.gui.SnakeGameService;
import de.example.reports.integrity.IntegrityManifest;
import de.example.reports.listener.ReportListener;
import de.example.reports.model.Sanction;
import de.example.reports.moderation.ModerationService;
import de.example.reports.util.Messages;
import de.example.reports.util.PaperEquipmentSettings;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;

public final class ReportsPlugin extends JavaPlugin {
    private Database database;
    private PluginSettings settings;
    private Messages messages;
    private DiscordService discord;
    private ReportGuiService gui;
    private AdminReportGuiService adminGui;
    private SnakeGameService snake;
    @Override
    public void onEnable() {
        try {
            Path pluginJar = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            IntegrityManifest.verify(pluginJar);
            getLogger().info("Plugin integrity verified.");
        } catch (Exception e) {
            getLogger().severe("Plugin integrity check failed; refusing to start: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PaperEquipmentSettings.enableAttributeSwapping(this);
        saveDefaultConfig();
        settings = PluginSettings.from(getConfig());
        messages = new Messages(getConfig());

        try {
            database = new Database(new File(getDataFolder(), settings.databaseFile()));
            getLogger().info("SQLite database connected.");
        } catch (Exception e) {
            getLogger().severe("Database startup failed; disabling plugin: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GrimHook grim = new GrimHook(this, database);
        getLogger().info("GrimAC " + (grim.available() ? "detected" : "not detected") + ".");
        SignInput sign = new SignInput(this);
        ModerationService moderation = new ModerationService(this, database);
        snake = new SnakeGameService(this);
        discord = new DiscordService(this, database, moderation, settings);
        discord.start();
        gui = new ReportGuiService(this, database, messages, sign, grim, discord, snake, settings);
        adminGui = new AdminReportGuiService(this, database, moderation);
        new ReportListener(this, database, gui, adminGui, moderation, sign);

        command("report", new ReportCommand(gui, messages));
        command("reports", new ReportsCommand(this, database, adminGui));
        command("snake", new SnakeCommand(snake));
        PluginCommand flagsCommand = getCommand("flags");
        if (flagsCommand == null) {
            throw new IllegalStateException("Missing command flags");
        }
        FlagsCommand flagsExecutor = new FlagsCommand(this, database, grim);
        flagsCommand.setExecutor(flagsExecutor);
        flagsCommand.setTabCompleter(flagsExecutor);
        command("ban", new ModerationCommand(moderation, Sanction.Type.BAN, false, false));
        command("tempban", new ModerationCommand(moderation, Sanction.Type.BAN, true, false));
        command("mute", new ModerationCommand(moderation, Sanction.Type.MUTE, false, false));
        command("tempmute", new ModerationCommand(moderation, Sanction.Type.MUTE, true, false));
        command("unban", new ModerationCommand(moderation, Sanction.Type.BAN, false, true));
        getLogger().info("Reports enabled.");
    }

    private void command(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Missing command " + name);
        }
        command.setExecutor(executor);
    }

    public void reloadPlugin() {
        reloadConfig();
        settings = PluginSettings.from(getConfig());
        messages.reload(getConfig());
        gui.settings(settings);
        discord.reconfigure(settings);
    }

    public Messages messages() {
        return messages;
    }

    @Override
    public void onDisable() {
        if (snake != null) {
            snake.shutdown();
        }
        if (discord != null) {
            discord.shutdown();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("Reports disabled.");
    }
}
