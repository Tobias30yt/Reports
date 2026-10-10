package de.example.reports.grim;

import de.example.reports.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Captures GrimAC's public Bukkit flag events without linking against Grim internals. */
public final class GrimHook implements Listener {
    private static final String FLAG_EVENT_CLASS = "ac.grim.grimac.api.events.FlagEvent";

    private final JavaPlugin plugin;
    private final Database database;
    private final Plugin grimPlugin;
    private final ConcurrentMap<UUID, String> latestFlags = new ConcurrentHashMap<>();
    private boolean listenerRegistered;

    public GrimHook(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.grimPlugin = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (grimPlugin != null && grimPlugin.isEnabled()) {
            registerFlagListener();
        }
    }

    public boolean available() {
        return grimPlugin != null && grimPlugin.isEnabled() && listenerRegistered;
    }

    public String flags(Player player) {
        if (!available()) {
            return grimPlugin == null ? "GrimAC is not installed." : "GrimAC flag listener is unavailable.";
        }
        return latestFlags.getOrDefault(player.getUniqueId(), "No GrimAC flags captured during this session.");
    }

    public void clearLatestFlags(UUID playerUuid) {
        latestFlags.remove(playerUuid);
    }

    private void registerFlagListener() {
        try {
            Class<?> rawEventClass = grimPlugin.getClass().getClassLoader().loadClass(FLAG_EVENT_CLASS);
            Class<? extends Event> eventClass = rawEventClass.asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> capture(event);
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.MONITOR,
                    executor,
                    plugin,
                    true
            );
            listenerRegistered = true;
            plugin.getLogger().info("Listening for GrimAC flag events.");
        } catch (ClassNotFoundException | ClassCastException | LinkageError e) {
            plugin.getLogger().warning(
                    "GrimAC is installed, but its public Bukkit FlagEvent is unavailable: " + e.getMessage()
            );
        }
    }

    private void capture(Event event) {
        try {
            Object user = invoke(event, "getUser");
            UUID playerUuid = playerUuid(user);
            String playerName = playerName(user);
            Object check = invoke(event, "getCheck");
            String checkName = checkName(check);
            String details = verbose(event);
            Object violations = invokeIfPresent(check, "getViolations");
            if (violations != null) {
                details = details.isBlank()
                        ? "Violation level: " + violations
                        : details + " (violation level: " + violations + ")";
            }

            String storedDetails = details.isBlank() ? "No additional details." : details;
            latestFlags.put(playerUuid, checkName + ": " + storedDetails);
            database.saveGrimFlag(playerUuid, playerName, checkName, storedDetails)
                    .exceptionally(error -> {
                        plugin.getLogger().warning(
                                "Could not store GrimAC flag for " + playerName + ": " + error.getMessage()
                        );
                        return null;
                    });
        } catch (ReflectiveOperationException | ClassCastException | IllegalStateException e) {
            plugin.getLogger().warning("Could not read GrimAC flag event: " + e.getMessage());
        }
    }

    private UUID playerUuid(Object user) throws ReflectiveOperationException {
        Object value = invokeIfPresent(user, "getUniqueId");
        if (value == null) {
            value = invokeIfPresent(user, "getUUID");
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        throw new IllegalStateException("GrimAC user does not expose a UUID.");
    }

    private String playerName(Object user) throws ReflectiveOperationException {
        Object profile = invokeIfPresent(user, "getProfile");
        Object name = profile == null ? null : invokeIfPresent(profile, "getName");
        if (name == null) {
            name = invokeIfPresent(user, "getName");
        }
        return name == null ? "Unknown" : name.toString();
    }

    private String checkName(Object check) throws ReflectiveOperationException {
        Object name = invokeIfPresent(check, "getCheckName");
        if (name == null) {
            name = invokeIfPresent(check, "getName");
        }
        if (name == null) {
            name = check.getClass().getSimpleName();
        }
        return name.toString();
    }

    private String verbose(Object event) throws ReflectiveOperationException {
        Object value = invokeIfPresent(event, "getVerbose");
        if (value instanceof Supplier<?> supplier) {
            value = supplier.get();
        }
        return value == null ? "" : value.toString();
    }

    private Object invokeIfPresent(Object target, String methodName) throws ReflectiveOperationException {
        Method method = publicMethod(target.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("GrimAC " + methodName + " call failed.", e.getCause());
        }
    }

    private Method publicMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                return method;
            }
        } catch (NoSuchMethodException ignored) {
            // Search public API interfaces below; Grim implementations may be package-private.
        }

        for (Class<?> interfaceType : type.getInterfaces()) {
            Method method = publicMethod(interfaceType, name);
            if (method != null) {
                return method;
            }
        }
        Class<?> parent = type.getSuperclass();
        return parent == null ? null : publicMethod(parent, name);
    }

    private Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Object value = invokeIfPresent(target, methodName);
        if (value == null) {
            throw new IllegalStateException("GrimAC event is missing " + methodName + "().");
        }
        return value;
    }
}
