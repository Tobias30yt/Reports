package de.example.reports.command;

import de.example.reports.moderation.ModerationService;
import de.example.reports.model.Sanction;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import java.time.Duration;
import java.util.Locale;

/** /ban, /tempban, /mute, /tempmute and /unban backed by the Reports sanction store. */
public final class ModerationCommand implements CommandExecutor {
 private final ModerationService moderation; private final Sanction.Type type; private final boolean temporary; private final boolean pardon;
 public ModerationCommand(ModerationService moderation,Sanction.Type type,boolean temporary,boolean pardon){this.moderation=moderation;this.type=type;this.temporary=temporary;this.pardon=pardon;}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){String permission="reports."+label.toLowerCase(Locale.ROOT);if(!sender.hasPermission(permission)){sender.sendMessage("§cDafür hast du keine Berechtigung.");return true;}if(args.length<(temporary?2:1)){sender.sendMessage("§cBenutzung: /"+label+" <spieler>"+(temporary?" <z.B. 1h, 7d>":"")+" [grund]");return true;}OfflinePlayer target=Bukkit.getOfflinePlayerIfCached(args[0]);if(target==null){sender.sendMessage("§cDieser Spieler ist nicht bekannt.");return true;}String name=target.getName()==null?args[0]:target.getName();if(pardon){moderation.pardon(sender,target.getUniqueId(),name);return true;}Duration duration=temporary?duration(args[1]):null;if(temporary&&duration==null){sender.sendMessage("§cUngültige Dauer. Nutze z. B. 30m, 12h oder 7d.");return true;}int reasonStart=temporary?2:1;String reason=args.length>reasonStart?String.join(" ",java.util.Arrays.copyOfRange(args,reasonStart,args.length)):"Manual "+label+" by "+sender.getName();moderation.apply(sender,target.getUniqueId(),name,type,duration,reason);return true;}
 private Duration duration(String input){try{long value=Long.parseLong(input.substring(0,input.length()-1));if(value<1)return null;return switch(input.substring(input.length()-1).toLowerCase(Locale.ROOT)){case "m"->Duration.ofMinutes(value);case "h"->Duration.ofHours(value);case "d"->Duration.ofDays(value);default->null;};}catch(Exception ignored){return null;}}
}
