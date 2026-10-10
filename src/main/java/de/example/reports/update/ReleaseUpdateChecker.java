package de.example.reports.update;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the repository's latest stable release on startup and every two days. */
public final class ReleaseUpdateChecker {
    private static final URI LATEST = URI.create("https://api.github.com/repos/Tobias30yt/Reports/releases/latest");
    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern URL = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.jar)\\\"");
    private final JavaPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private ScheduledTask periodic;

    public ReleaseUpdateChecker(JavaPlugin plugin) { this.plugin=plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("reports.update-checker.enabled", true)) return;
        Bukkit.getAsyncScheduler().runNow(plugin, task -> check());
        periodic=Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> check(), 48, 48, TimeUnit.HOURS);
    }

    private void check() {
        try {
            HttpRequest request=HttpRequest.newBuilder(LATEST).timeout(Duration.ofSeconds(15))
                    .header("Accept","application/vnd.github+json")
                    .header("User-Agent","ReportsPlugin/"+plugin.getPluginMeta().getVersion()).GET().build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) throw new IllegalStateException("GitHub returned HTTP "+response.statusCode());
            Matcher tagMatch=TAG.matcher(response.body());
            if(!tagMatch.find()) throw new IllegalStateException("Latest release response did not contain tag_name");
            String tag=tagMatch.group(1);
            Matcher urlMatch=URL.matcher(response.body());
            String jarUrl=null;
            while(urlMatch.find()) if(urlMatch.group(1).endsWith("/Reports.jar")){jarUrl=urlMatch.group(1);break;}
            if(jarUrl==null && urlMatch.reset().find()) jarUrl=urlMatch.group(1);
            if(jarUrl==null) throw new IllegalStateException("Latest release has no JAR download asset");
            String installed=plugin.getPluginMeta().getVersion();
            if(compareVersions(tag,installed)<=0) return;
            String releaseUrl="https://github.com/Tobias30yt/Reports/releases/tag/"+tag;
            String download=jarUrl;
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> notifyOperators(tag,installed,releaseUrl,download));
        } catch(Exception e) {
            plugin.getLogger().warning("Could not check the latest GitHub release: "+e.getMessage());
        }
    }

    private void notifyOperators(String latest,String installed,String release,String jar) {
        String message="Please download the latest Reports release ("+latest+", installed: "+installed+"): "+release+" | JAR: "+jar;
        plugin.getLogger().warning(message);
        for(Player player:new ArrayList<>(Bukkit.getOnlinePlayers())) if(player.isOp())
            player.getScheduler().run(plugin, task -> player.sendMessage(message), null);
    }

    private static int compareVersions(String a,String b) {
        List<Integer> left=parts(a),right=parts(b); int size=Math.max(left.size(),right.size());
        for(int i=0;i<size;i++){int x=i<left.size()?left.get(i):0,y=i<right.size()?right.get(i):0;if(x!=y)return Integer.compare(x,y);}return 0;
    }
    private static List<Integer> parts(String version) {
        Matcher matcher=Pattern.compile("\\d+").matcher(version); List<Integer> parts=new ArrayList<>();
        while(matcher.find())try{parts.add(Integer.parseInt(matcher.group()));}catch(NumberFormatException ignored){parts.add(Integer.MAX_VALUE);}
        return parts;
    }

    public void shutdown(){if(periodic!=null)periodic.cancel();}
}
