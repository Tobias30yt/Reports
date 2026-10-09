package de.example.reports.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class PaperEquipmentSettings {
    private static final String CONFIG_CLASS = "io.papermc.paper.configuration.GlobalConfiguration";

    private PaperEquipmentSettings() {
    }

    public static void enableEquipmentUpdates(JavaPlugin plugin) {
        try {
            ClassLoader serverClassLoader = plugin.getServer().getClass().getClassLoader();
            Class<?> configurationClass = serverClassLoader.loadClass(CONFIG_CLASS);
            Method getConfiguration = configurationClass.getMethod("get");
            Object configuration = getConfiguration.invoke(null);
            if (configuration == null) {
                throw new IllegalStateException("Paper global configuration is not initialized.");
            }

            Field unsupportedSettingsField = configurationClass.getField("unsupportedSettings");
            Object unsupportedSettings = unsupportedSettingsField.get(configuration);
            if (unsupportedSettings == null) {
                throw new IllegalStateException("Paper unsupported settings are not initialized.");
            }

            Field equipmentUpdatesField = unsupportedSettings.getClass()
                    .getField("updateEquipmentOnPlayerActions");
            equipmentUpdatesField.setBoolean(unsupportedSettings, true);
            plugin.getLogger().info("Enabled Paper equipment updates on player actions.");
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
            plugin.getLogger().warning(
                    "Could not enable Paper equipment updates automatically. "
                            + "Set unsupported-settings.update-equipment-on-player-actions to true "
                            + "in config/paper-global.yml. Cause: " + e.getMessage()
            );
        } catch (IllegalStateException e) {
            plugin.getLogger().warning(
                    "Could not enable Paper equipment updates automatically: " + e.getMessage()
            );
        }
    }
}
