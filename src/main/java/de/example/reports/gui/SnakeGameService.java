package de.example.reports.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Click-controlled, combat-aware Snake session after a report. */
public final class SnakeGameService implements Listener, PluginMessageListener {
    private static final String HELLO="claims:snake_hello", ACTION="claims:snake_action", SIGNAL="claims:snake";
    private static final int MAGIC=0x534E414B;
    private static final int WIDTH = 7;
    private static final int HEIGHT = 3;
    private static final int TICKS_PER_MOVE = 20;
    private static final int[] BOARD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final JavaPlugin plugin;
    private final Map<UUID, Game> games = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combat = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> nativeClients = ConcurrentHashMap.newKeySet();

    public SnakeGameService(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HELLO, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, ACTION, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, SIGNAL);
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (channel.equals(HELLO)) {
            if (data.length == 4 && java.nio.ByteBuffer.wrap(data).getInt() == MAGIC) nativeClients.add(player.getUniqueId());
            return;
        }
        if (!channel.equals(ACTION) || data.length != 13) return;
        java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(data);
        if (b.getInt()!=MAGIC || b.get()!=2) return;
        long token=b.getLong(); Game game=games.get(player.getUniqueId());
        if (game!=null && game.nativeClient && game.session==token) end(player);
    }
    private void signal(Player player, int op, long token) {
        if (player.isOnline()) player.sendPluginMessage(plugin, SIGNAL, java.nio.ByteBuffer.allocate(13).putInt(MAGIC).put((byte)op).putLong(token).array());
    }

    public void offer(Player player) {
        String message = plugin.getConfig().getString(
                "reports.waiting-game.messages.report-pending",
                "<yellow>Your report is pending. Enjoy Snake while you wait!</yellow>"
        );
        Component chatMessage = MiniMessage.miniMessage().deserialize(message);
        if (plugin.getConfig().getBoolean("reports.waiting-game.enabled", true)) {
            chatMessage = chatMessage.append(Component.newline()).append(
                    Component.text("[Snake starten]")
                            .color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/snake"))
                            .hoverEvent(HoverEvent.showText(Component.text("Klicken, um Snake zu öffnen")))
            );
        }
        player.sendMessage(chatMessage);
    }

    public void open(Player player) {
        if (!plugin.getConfig().getBoolean("reports.waiting-game.enabled", true)
                || games.containsKey(player.getUniqueId())) {
            return;
        }
        if (tagged(player)) {
            String message = plugin.getConfig().getString(
                    "reports.waiting-game.messages.combat-blocked",
                    "<red>You cannot start Snake during combat.</red>"
            );
            player.sendMessage(MiniMessage.miniMessage().deserialize(message));
            return;
        }

        Game game = new Game(player.isInvulnerable());
        game.nativeClient = nativeClients.contains(player.getUniqueId());
        game.session = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        games.put(player.getUniqueId(), game);
        player.setInvulnerable(true);

        if (game.nativeClient) {
            signal(player, 0, game.session);
            game.task = player.getScheduler().runAtFixedRate(plugin, task -> {
                if (games.get(player.getUniqueId()) == game && ++game.ticksUntilMove > 20 * 60 * 30) end(player);
            }, () -> games.remove(player.getUniqueId(), game), 1, 1);
            return;
        }

        SnakeInventory holder = new SnakeInventory();
        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                Component.text("Snake - Pfeile anklicken")
        );
        holder.inventory(inventory);
        game.inventory = inventory;
        draw(inventory, game);
        game.task = player.getScheduler().runAtFixedRate(
                plugin,
                task -> tick(player, game),
                () -> games.remove(player.getUniqueId(), game),
                1,
                1
        );
        player.openInventory(inventory);
    }

    public void shutdown() {
        for (UUID playerId : games.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                end(player);
            } else {
                games.remove(playerId);
            }
        }
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
    }

    private void tick(Player player, Game game) {
        if (games.get(player.getUniqueId()) != game || game.gameOver) {
            return;
        }

        if (!game.started || ++game.ticksUntilMove < TICKS_PER_MOVE) {
            return;
        }
        game.ticksUntilMove = 0;
        game.step();
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof SnakeInventory) {
            draw(player.getOpenInventory().getTopInventory(), game);
        }
    }

    private void draw(Inventory inventory, Game game) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, item(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        for (int slot : BOARD_SLOTS) {
            inventory.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, " "));
        }

        if (game.gameOver) {
            inventory.setItem(22, item(Material.RED_STAINED_GLASS_PANE, game.won ? "You win!" : "Game over"));
            inventory.setItem(31, item(Material.GOLD_INGOT, "Final score: " + game.score));
            inventory.setItem(49, item(Material.SLIME_BALL, "Play again"));
            inventory.setItem(53, item(Material.BARRIER, "Exit game"));
            return;
        }

        for (int segment : game.snake) {
            Material material = segment == game.snake.peekFirst()
                    ? Material.EMERALD_BLOCK
                    : Material.GREEN_STAINED_GLASS_PANE;
            inventory.setItem(BOARD_SLOTS[segment], item(material, segment == game.snake.peekFirst() ? "Snake" : " "));
        }
        inventory.setItem(BOARD_SLOTS[game.food], item(Material.REDSTONE_BLOCK, "Food"));
        inventory.setItem(4, item(
                game.started ? Material.GOLD_INGOT : Material.CLOCK,
                game.started ? "Score: " + game.score : "Pfeiltaste anklicken, um zu starten"
        ));
        inventory.setItem(40, direction("↑", "Up"));
        inventory.setItem(48, direction("←", "Left"));
        inventory.setItem(49, direction("↓", "Down"));
        inventory.setItem(50, direction("→", "Right"));
        inventory.setItem(53, item(Material.BARRIER, "End game"));
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof SnakeInventory)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        Game game = games.get(player.getUniqueId());
        if (game == null) {
            return;
        }

        switch (event.getRawSlot()) {
            case 40 -> game.turn(0, -1);
            case 48 -> game.turn(-1, 0);
            case 49 -> {
                if (game.gameOver) {
                    game.reset();
                    draw(game.inventory, game);
                } else {
                    game.turn(0, 1);
                }
            }
            case 50 -> game.turn(1, 0);
            case 53 -> end(player);
            default -> {
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof SnakeInventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void move(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder(false) instanceof SnakeInventory
                || event.getDestination().getHolder(false) instanceof SnakeInventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof SnakeInventory
                && event.getPlayer() instanceof Player player) {
            end(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (games.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent attack) {
            tagCombat(player);
            Player attacker = playerAttacker(attack.getDamager());
            if (attacker != null && !games.containsKey(attacker.getUniqueId())) {
                tagCombat(attacker);
            }
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (tagged(player) && plugin.getConfig().getBoolean("reports.waiting-game.combat-logging.enabled", false)) {
            String command = plugin.getConfig()
                    .getString("reports.waiting-game.combat-logging.command", "")
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
            if (!command.isBlank()) {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    plugin.getLogger().warning("Combat-logging command was not handled: " + command);
                }
            }
        }
        end(player);
        combat.remove(player.getUniqueId());
        nativeClients.remove(player.getUniqueId());
    }

    private boolean tagged(Player player) {
        return combat.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private void tagCombat(Player player) {
        long durationSeconds = Math.max(
                0,
                plugin.getConfig().getLong("reports.waiting-game.combat-tag-seconds", 15)
        );
        if (durationSeconds == 0) {
            combat.remove(player.getUniqueId());
            return;
        }
        combat.put(player.getUniqueId(), System.currentTimeMillis() + durationSeconds * 1000L);
    }

    private Player playerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return null;
    }

    private void end(Player player) {
        Game game = games.remove(player.getUniqueId());
        if (game == null) {
            return;
        }
        if (game.task != null) {
            game.task.cancel();
        }
        if (game.nativeClient) signal(player, 1, game.session);
        player.setInvulnerable(game.wasInvulnerable);
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof SnakeInventory) {
            player.closeInventory();
        }
    }

    private ItemStack item(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack direction(String arrow, String action) {
        ItemStack item = item(Material.ARROW, arrow);
        ItemMeta meta = item.getItemMeta();
        meta.lore(java.util.List.of(Component.text(action, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private static final class Game {
        private final ArrayDeque<Integer> snake = new ArrayDeque<>();
        private final boolean wasInvulnerable;
        private Inventory inventory;
        private ScheduledTask task;
        private int dx = 1;
        private int dy;
        private int nextDx = 1;
        private int nextDy;
        private int food;
        private int score;
        private boolean started;
        private boolean directionQueued;
        private boolean gameOver;
        private boolean won;
        private boolean nativeClient;
        private long session;
        private boolean forwardDown;
        private boolean backwardDown;
        private boolean leftDown;
        private boolean rightDown;
        private int ticksUntilMove;

        private Game(boolean wasInvulnerable) {
            this.wasInvulnerable = wasInvulnerable;
            reset();
        }

        private void turn(int x, int y) {
            if (gameOver) {
                return;
            }

            if (!started) {
                dx = x;
                dy = y;
                nextDx = x;
                nextDy = y;
                started = true;
                return;
            }

            int currentDx = directionQueued ? nextDx : dx;
            int currentDy = directionQueued ? nextDy : dy;
            if (x == -currentDx && y == -currentDy || x == currentDx && y == currentDy) {
                return;
            }
            nextDx = x;
            nextDy = y;
            directionQueued = true;
        }

        private boolean trackInput(boolean forward, boolean backward, boolean left, boolean right) {
            boolean wasStarted = started;
            int oldDx = directionQueued ? nextDx : dx;
            int oldDy = directionQueued ? nextDy : dy;
            if (forward && !forwardDown) {
                turn(0, -1);
            } else if (backward && !backwardDown) {
                turn(0, 1);
            } else if (left && !leftDown) {
                turn(-1, 0);
            } else if (right && !rightDown) {
                turn(1, 0);
            }
            forwardDown = forward;
            backwardDown = backward;
            leftDown = left;
            rightDown = right;
            int newDx = directionQueued ? nextDx : dx;
            int newDy = directionQueued ? nextDy : dy;
            return wasStarted != started || oldDx != newDx || oldDy != newDy;
        }

        private void step() {
            if (!started || gameOver) {
                return;
            }
            if (directionQueued) {
                dx = nextDx;
                dy = nextDy;
                directionQueued = false;
            }

            int head = snake.peekFirst();
            int x = head % WIDTH + dx;
            int y = head / WIDTH + dy;
            if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
                gameOver = true;
                return;
            }

            int next = y * WIDTH + x;
            boolean eating = next == food;
            boolean hitsBody = snake.contains(next) && (eating || snake.peekLast() != next);
            if (hitsBody) {
                gameOver = true;
                return;
            }

            snake.addFirst(next);
            if (eating) {
                score++;
                if (!placeFood()) {
                    gameOver = true;
                    won = true;
                }
            } else {
                snake.removeLast();
            }
        }

        private boolean placeFood() {
            int empty = WIDTH * HEIGHT - snake.size();
            if (empty == 0) {
                return false;
            }

            int candidate = ThreadLocalRandom.current().nextInt(empty);
            for (int cell = 0; cell < WIDTH * HEIGHT; cell++) {
                if (!snake.contains(cell) && candidate-- == 0) {
                    food = cell;
                    return true;
                }
            }
            throw new IllegalStateException("Could not place Snake food on an available cell.");
        }

        private void reset() {
            snake.clear();
            snake.add(10);
            snake.add(9);
            snake.add(8);
            dx = 0;
            dy = 0;
            nextDx = 0;
            nextDy = 0;
            started = false;
            directionQueued = false;
            ticksUntilMove = 0;
            forwardDown = false;
            backwardDown = false;
            leftDown = false;
            rightDown = false;
            score = 0;
            gameOver = false;
            won = false;
            if (!placeFood()) {
                throw new IllegalStateException("Could not place initial Snake food.");
            }
        }
    }
}
