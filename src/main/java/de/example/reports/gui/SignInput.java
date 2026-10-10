package de.example.reports.gui;

import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** A per-player virtual sign prompt; unrelated sign edits cannot consume or answer it. */
public final class SignInput implements Listener {
    private record Prompt(Location position, Consumer<List<Component>> callback) {}
    private final JavaPlugin plugin;
    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();

    public SignInput(JavaPlugin plugin) { this.plugin = plugin; plugin.getServer().getPluginManager().registerEvents(this, plugin); }
    public void open(Player player, Consumer<List<Component>> callback) { open(player, "", callback); }
    public void open(Player player, String title, Consumer<List<Component>> callback) {
        Location at = player.getLocation().getBlock().getLocation();
        BlockData data = Material.OAK_SIGN.createBlockData();
        Sign state = (Sign) data.createBlockState();
        state.setAllowedEditorUniqueId(player.getUniqueId());
        Prompt prompt = new Prompt(at, callback);
        pending.put(player.getUniqueId(), prompt);
        player.sendBlockChange(at, data);
        player.sendBlockUpdate(at, state);
        player.openVirtualSign(at, Side.FRONT);
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (pending.remove(player.getUniqueId(), prompt)) {
                player.sendBlockChange(at, at.getBlock().getBlockData());
            }
        }, 1200L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void input(UncheckedSignChangeEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Prompt prompt = pending.get(id);
        if (prompt == null) return;
        var edited = event.getEditedBlockPosition();
        if (edited.blockX() != prompt.position().getBlockX() || edited.blockY() != prompt.position().getBlockY()
                || edited.blockZ() != prompt.position().getBlockZ()) return;
        if (!pending.remove(id, prompt)) return;
        event.setCancelled(true);
        prompt.callback().accept(event.lines());
    }

    @EventHandler public void quit(PlayerQuitEvent event) { clear(event.getPlayer().getUniqueId()); }
    public void clear(UUID id) { pending.remove(id); }
}
