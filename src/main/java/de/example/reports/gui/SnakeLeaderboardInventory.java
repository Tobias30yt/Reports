package de.example.reports.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class SnakeLeaderboardInventory implements InventoryHolder {
    private Inventory inventory;
    void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
