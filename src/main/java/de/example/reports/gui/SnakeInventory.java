package de.example.reports.gui;
import org.bukkit.inventory.*; import org.jetbrains.annotations.NotNull;
public final class SnakeInventory implements InventoryHolder {private Inventory inventory;public void inventory(Inventory value){inventory=value;}public @NotNull Inventory getInventory(){return inventory;}}
