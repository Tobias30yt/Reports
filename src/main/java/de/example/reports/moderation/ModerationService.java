package de.example.reports.moderation;

import de.example.reports.database.Database;
import de.example.reports.model.Sanction;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent ban/mute enforcement used by the report staff UI. */
public final class ModerationService {
 private final JavaPlugin plugin; private final Database database; private final Set<UUID> muted=ConcurrentHashMap.newKeySet();
 public ModerationService(JavaPlugin plugin,Database database){this.plugin=plugin;this.database=database;}
 public void check(Player player){database.sanction(player.getUniqueId(),Sanction.Type.BAN).thenAccept(ban->plugin.getServer().getScheduler().runTask(plugin,()->{if(ban.isPresent()){player.kick(Component.text(message("You are banned",ban.get())));return;}database.sanction(player.getUniqueId(),Sanction.Type.MUTE).thenAccept(mute->plugin.getServer().getScheduler().runTask(plugin,()->{if(mute.isPresent())muted.add(player.getUniqueId());else muted.remove(player.getUniqueId());}));}));}
 public boolean muted(Player player){return muted.contains(player.getUniqueId());}
 public void apply(CommandSender staff,UUID target,String targetName,Sanction.Type type,Duration duration,String reason){Instant expires=duration==null?null:Instant.now().plus(duration);database.sanction(target,targetName,type,expires,reason).thenRun(()->plugin.getServer().getScheduler().runTask(plugin,()->{Player online=plugin.getServer().getPlayer(target);if(type==Sanction.Type.BAN&&online!=null)online.kick(Component.text(message("You are banned",new Sanction(target,targetName,type,expires,reason))));if(type==Sanction.Type.MUTE&&online!=null)muted.add(target);staff.sendMessage("§a"+targetName+" wurde "+(type==Sanction.Type.BAN?"gebannt.":"stummgeschaltet."));}));}
 public void pardon(CommandSender staff,UUID target,String targetName){database.removeSanction(target,Sanction.Type.BAN).thenCompose(v->database.removeSanction(target,Sanction.Type.MUTE)).thenRun(()->plugin.getServer().getScheduler().runTask(plugin,()->{muted.remove(target);staff.sendMessage("§aSanktionen für "+targetName+" wurden aufgehoben.");}));}
 private String message(String title,Sanction sanction){return "§4§l"+title+"\n§7Reason: §f"+sanction.reason()+"\n§7Remaining ban time: §e"+(sanction.permanent()?"Permanent":remaining(sanction.expiresAt()));}
 private String remaining(Instant expiresAt){Duration duration=Duration.between(Instant.now(),expiresAt);if(duration.isNegative()||duration.isZero())return "Expired";long seconds=duration.getSeconds();long days=seconds/86400;seconds%=86400;long hours=seconds/3600;seconds%=3600;long minutes=seconds/60;seconds%=60;StringBuilder text=new StringBuilder();if(days>0)text.append(days).append(days==1?" day ":" days ");if(hours>0)text.append(hours).append(hours==1?" hour ":" hours ");if(minutes>0)text.append(minutes).append(minutes==1?" minute ":" minutes ");text.append(seconds).append(seconds==1?" second":" seconds");return text.toString().trim();}
}
