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

/** Checks stable GitHub releases on startup and every 48 hours. */
public final class ReleaseUpdateChecker {
    private static final URI RELEASES=URI.create("https://api.github.com/repos/Tobias30yt/Reports/releases?per_page=100");
    private static final Pattern TAG=Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern URL=Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.jar)\\\"");
    private final JavaPlugin plugin;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private ScheduledTask periodic;
    public ReleaseUpdateChecker(JavaPlugin plugin){this.plugin=plugin;}
    public void start(){if(!plugin.getConfig().getBoolean("reports.update-checker.enabled",true))return;Bukkit.getAsyncScheduler().runNow(plugin,t->check());periodic=Bukkit.getAsyncScheduler().runAtFixedRate(plugin,t->check(),48,48,TimeUnit.HOURS);}
    private void check(){try{
        HttpRequest request=HttpRequest.newBuilder(RELEASES).timeout(Duration.ofSeconds(15)).header("Accept","application/vnd.github+json").header("User-Agent","ReportsPlugin/"+plugin.getPluginMeta().getVersion()).GET().build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("GitHub returned HTTP "+response.statusCode());
        Matcher tagMatcher=TAG.matcher(response.body());List<String> tags=new ArrayList<>();while(tagMatcher.find())tags.add(tagMatcher.group(1));if(tags.isEmpty())throw new IllegalStateException("No release tags in GitHub response");
        String installed=plugin.getPluginMeta().getVersion(),latest=tags.get(0);int behind=(int)tags.stream().filter(tag->compare(tag,installed)>0).count();if(behind==0)return;
        Matcher urlMatcher=URL.matcher(response.body());String jar=null;while(urlMatcher.find())if(urlMatcher.group(1).endsWith("/Reports.jar")){jar=urlMatcher.group(1);break;}if(jar==null)throw new IllegalStateException("Latest release has no Reports.jar asset");
        String release="https://github.com/Tobias30yt/Reports/releases/tag/"+latest,download=jar;Bukkit.getGlobalRegionScheduler().run(plugin,t->notifyOperators(behind,installed,latest,release,download));
    }catch(Exception e){plugin.getLogger().warning("Could not check the latest GitHub release: "+e.getMessage());}}
    private void notifyOperators(int behind,String installed,String latest,String release,String jar){String message="Reports update available: you are "+behind+" release"+(behind==1?"":"s")+" behind (installed "+installed+", latest "+latest+"). Download: "+release+" | JAR: "+jar;plugin.getLogger().warning(message);for(Player player:new ArrayList<>(Bukkit.getOnlinePlayers()))if(player.isOp())player.getScheduler().run(plugin,t->player.sendMessage(message),null);}
    private static int compare(String a,String b){List<Integer>x=parts(a),y=parts(b);for(int i=0;i<Math.max(x.size(),y.size());i++){int l=i<x.size()?x.get(i):0,r=i<y.size()?y.get(i):0;if(l!=r)return Integer.compare(l,r);}return 0;}
    private static List<Integer> parts(String value){Matcher matcher=Pattern.compile("\\d+").matcher(value);List<Integer>parts=new ArrayList<>();while(matcher.find())try{parts.add(Integer.parseInt(matcher.group()));}catch(NumberFormatException ignored){parts.add(Integer.MAX_VALUE);}return parts;}
    public void shutdown(){if(periodic!=null)periodic.cancel();}
}
