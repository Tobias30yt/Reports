package de.example.reports.grim;
import org.bukkit.Bukkit; import org.bukkit.entity.Player;
/** Deliberately avoids linking against Grim: its public API does not expose a stable generic violation snapshot. */
public final class GrimHook { private final boolean available; public GrimHook(){available=Bukkit.getPluginManager().getPlugin("GrimAC")!=null;} public boolean available(){return available;} public String flags(Player player){return available?"No active flags available.":"GrimAC is not installed.";} }
