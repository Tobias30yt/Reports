package de.example.reports.moderation;

import de.example.reports.database.Database;
import de.example.reports.model.Sanction;
import de.example.reports.language.Language;
import net.kyori.adventure.text.Component;
import de.example.reports.util.ColorText;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent sanctions with expiring, thread-safe mute feedback. */
public final class ModerationService {
 private final JavaPlugin plugin; private final Database database;
 private final Map<UUID,Sanction> muted=new ConcurrentHashMap<>();
 public ModerationService(JavaPlugin plugin,Database database){this.plugin=plugin;this.database=database;}
 public void check(Player player){database.sanction(player.getUniqueId(),Sanction.Type.BAN).thenAccept(ban->plugin.getServer().getScheduler().runTask(plugin,()->{if(ban.isPresent()){player.kick(ColorText.parse(message(player,ban.get())));return;}database.sanction(player.getUniqueId(),Sanction.Type.MUTE).thenAccept(mute->{if(mute.isPresent())muted.put(player.getUniqueId(),mute.get());else muted.remove(player.getUniqueId());});}));}
 public boolean muted(Player player){Sanction sanction=muted.get(player.getUniqueId());if(sanction==null)return false;if(!sanction.active()){muted.remove(player.getUniqueId(),sanction);database.sanction(player.getUniqueId(),Sanction.Type.MUTE);return false;}return true;}
 public Component muteMessage(Player player){Sanction sanction=muted.get(player.getUniqueId());return ColorText.parse(sanction==null?Language.choose(player,"Your mute has expired.","Deine Stummschaltung ist abgelaufen."):message(player,sanction));}
 public void quit(UUID id){muted.remove(id);}
 public void apply(CommandSender staff,UUID target,String targetName,Sanction.Type type,Duration duration,String reason){
  if(reason.length()>2000){staff.sendMessage(Language.choose(staff,"Reason too long (maximum 2000 characters).","Grund zu lang (maximal 2000 Zeichen)."));return;}
  Instant expires=duration==null?null:Instant.now().plus(duration);Sanction sanction=new Sanction(target,targetName,type,expires,reason);
  database.sanction(target,targetName,type,expires,reason).thenRun(()->plugin.getServer().getScheduler().runTask(plugin,()->{Player online=plugin.getServer().getPlayer(target);if(type==Sanction.Type.BAN&&online!=null)online.kick(ColorText.parse(message(online,sanction)));if(type==Sanction.Type.MUTE){muted.put(target,sanction);if(online!=null)online.sendMessage(ColorText.parse(message(online,sanction)));}staff.sendMessage("§a"+targetName+Language.choose(staff,type==Sanction.Type.BAN?" was banned.":" was muted.",type==Sanction.Type.BAN?" wurde gebannt.":" wurde stummgeschaltet."));}));
 }
 public void pardon(CommandSender staff,UUID target,String targetName){database.removeSanction(target,Sanction.Type.BAN).thenCompose(v->database.removeSanction(target,Sanction.Type.MUTE)).thenRun(()->plugin.getServer().getScheduler().runTask(plugin,()->{muted.remove(target);staff.sendMessage("§a"+targetName+Language.choose(staff,": sanctions removed.",": Sanktionen aufgehoben."));}));}
 private String message(Player player,Sanction sanction){boolean en=Language.english(player);String title=sanction.type()==Sanction.Type.BAN?(en?"You are banned":"Du bist gebannt"):(en?"You are muted":"Du bist stummgeschaltet");return "§c"+title+"\n§7"+(en?"Reason: ":"Grund: ")+"§f"+sanction.reason()+"\n§7"+(en?"Time remaining: ":"Verbleibende Zeit: ")+"§e"+(sanction.permanent()?(en?"Permanent":"Permanent"):remaining(sanction.expiresAt(),en));}
 public static String remaining(Instant until,boolean en){long seconds=Math.max(0,Duration.between(Instant.now(),until).getSeconds());long d=seconds/86400,h=(seconds%86400)/3600,m=(seconds%3600)/60,s=seconds%60;return (d>0?d+(en?" days ":" Tage "):"")+(h>0?h+(en?" hours ":" Stunden "):"")+(m>0?m+(en?" minutes ":" Minuten "):"")+s+(en?" seconds":" Sekunden");}
}
