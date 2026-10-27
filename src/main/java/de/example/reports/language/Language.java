package de.example.reports.language;

import net.kyori.adventure.text.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player language, persisted in player data and mirrored to LuckPerms. */
public final class Language implements Listener {
    private static Language instance;
    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID,Boolean> english=new ConcurrentHashMap<>();
    private final List<String[]> translations=new ArrayList<>();
    public Language(JavaPlugin plugin){
        this.plugin=plugin;key=new NamespacedKey(plugin,"language");instance=this;
        try(var stream=plugin.getResource("translations.tsv");var reader=new BufferedReader(new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8))){reader.lines().filter(l->!l.isBlank()&&!l.startsWith("#")).forEach(l->{String[] parts=l.split("\t",2);if(parts.length==2)translations.add(parts);});}catch(IOException e){throw new IllegalStateException(e);}
        translations.sort(Comparator.comparingInt((String[] v)->Math.max(v[0].length(),v[1].length())).reversed());
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        Objects.requireNonNull(plugin.getCommand("language")).setExecutor((sender,command,label,args)->{if(sender instanceof Player p)open(p);else sender.sendMessage("Use /language in-game.");return true;});
        for(Player p:Bukkit.getOnlinePlayers())load(p);
    }
    private boolean load(Player p){String saved=p.getPersistentDataContainer().get(key,PersistentDataType.STRING);boolean en=saved!=null?saved.equals("en"):!p.hasPermission("language.german")||p.hasPermission("language.englisch");english.put(p.getUniqueId(),en);return saved!=null;}
    public static boolean english(CommandSender sender){return !(sender instanceof Player p)||instance==null||instance.english.getOrDefault(p.getUniqueId(),true);}
    public static String choose(CommandSender sender,String en,String de){return english(sender)?en:de;}
    public static String text(CommandSender sender,String input){
        if(instance==null)return input;
        // Each source segment is translated once; replacement output is never translated again.
        boolean en=english(sender);StringBuilder out=new StringBuilder();
        for(int i=0;i<input.length();){boolean found=false;for(String[] pair:instance.translations){String from=pair[en?1:0],to=pair[en?0:1];if(!from.isEmpty()&&input.startsWith(from,i)&&boundary(input,from,i)){out.append(to);i+=from.length();found=true;break;}}if(!found)out.append(input.charAt(i++));}
        return out.toString();
    }
    private static boolean boundary(String text,String part,int start){int end=start+part.length();return !(Character.isLetterOrDigit(part.charAt(0))&&start>0&&Character.isLetterOrDigit(text.charAt(start-1))&&!(start>=2&&text.charAt(start-2)==\u0027§\u0027))&&!(Character.isLetterOrDigit(part.charAt(part.length()-1))&&end<text.length()&&Character.isLetterOrDigit(text.charAt(end)));}
    public static Component component(CommandSender sender,Component input){
        Component translated=input;if(input instanceof TextComponent t)translated=t.content(text(sender,t.content()));
        translated=translated.children(input.children().stream().map(c->component(sender,c)).toList());
        if(input.hoverEvent()!=null&&input.hoverEvent().action()==net.kyori.adventure.text.event.HoverEvent.Action.SHOW_TEXT)translated=translated.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(component(sender,(Component)input.hoverEvent().value())));
        return translated;
    }
    public static void send(CommandSender sender,String text){sender.sendMessage(text(sender,text));}
    public static void send(CommandSender sender,Component component){sender.sendMessage(component(sender,component));}
    @EventHandler public void join(PlayerJoinEvent event){Player p=event.getPlayer();boolean chosen=load(p);if(!chosen)plugin.getServer().getScheduler().runTaskLater(plugin,()->{if(p.isOnline())open(p);},30L);}
    public void open(Player p){Choice holder=new Choice();holder.inventory=Bukkit.createInventory(holder,27,Component.text("Choose language / Sprache wählen"));holder.inventory.setItem(11,item(Material.BLUE_WOOL,"English"));holder.inventory.setItem(15,item(Material.LIME_WOOL,"Deutsch"));p.openInventory(holder.inventory);}
    private ItemStack item(Material material,String name){ItemStack item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(Component.text(name));item.setItemMeta(meta);return item;}
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder(false) instanceof Choice))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p)||event.getClickedInventory()!=event.getView().getTopInventory()||event.isShiftClick()||event.getClick().isKeyboardClick())return;
        if(event.getSlot()!=11&&event.getSlot()!=15)return;boolean en=event.getSlot()==11;
        p.getPersistentDataContainer().set(key,PersistentDataType.STRING,en?"en":"de");english.put(p.getUniqueId(),en);
        if(Bukkit.getPluginManager().isPluginEnabled("LuckPerms")){
            // UUID and fixed node names only. Local preference remains valid without LuckPerms.
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"lp user "+p.getUniqueId()+" permission set language.englisch "+en);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"lp user "+p.getUniqueId()+" permission set language.german "+!en);
        }
        plugin.getServer().getScheduler().runTask(plugin,()->{p.closeInventory();p.sendMessage(en?"§aLanguage set to English. Use /language to change it.":"§aSprache auf Deutsch gesetzt. Ändern mit /language.");});
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder(false) instanceof Choice)event.setCancelled(true);}
    public static void inventory(Player player,Inventory inventory){
        for(ItemStack stack:inventory.getContents()){
            if(stack==null||!stack.hasItemMeta())continue;var meta=stack.getItemMeta();
            if(meta.displayName()!=null)meta.displayName(component(player,meta.displayName()));
            // Free-form report reasons and anticheat data must never be translated.
            if(meta.lore()!=null&&stack.getType()!=Material.WRITABLE_BOOK&&stack.getType()!=Material.REDSTONE)meta.lore(meta.lore().stream().map(c->component(player,c)).toList());
            stack.setItemMeta(meta);
        }
    }
    private static final class Choice implements InventoryHolder {private Inventory inventory;public Inventory getInventory(){return inventory;}}
}
