package de.example.reports.api;

import de.example.reports.database.Database;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

/** HTTPS synchronization of a transactionally persisted SQLite outbox. */
public final class ReportsSync implements AutoCloseable {
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final JavaPlugin plugin;
    private final Database db;
    private long lastWarning;
    public ReportsSync(JavaPlugin plugin,Database db) {
        this.plugin=plugin;this.db=db;
        File file=new File(plugin.getDataFolder(),"api.yml");
        if(!file.exists())plugin.saveResource("api.yml",false);
        YamlConfiguration c=YamlConfiguration.loadConfiguration(file);
        if(!c.getBoolean("enabled",false))return;
        URI endpoint;
        try { endpoint=URI.create(c.getString("endpoint","")); }
        catch(IllegalArgumentException error){plugin.getLogger().warning("Reports API disabled: invalid endpoint in api.yml.");return;}
        String key=c.getString("key","");
        if(!"https".equals(endpoint.getScheme())||endpoint.getUserInfo()!=null||!key.matches("tw_reports_[a-f0-9]{64}")) {
            plugin.getLogger().warning("Reports API disabled: configure a valid HTTPS endpoint and private server key in api.yml.");return;
        }
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        worker.scheduleWithFixedDelay(()->{
            try {
                var batch=db.apiBatch().get(10,TimeUnit.SECONDS);
                if(batch.isEmpty())return;
                String body="{\"events\":["+String.join(",",batch.stream().map(Database.ApiEvent::json).toList())+"]}";
                var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").header("Authorization","Bearer "+key).POST(HttpRequest.BodyPublishers.ofString(body)).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                long last=batch.getLast().sequence();
                if(response.statusCode()!=200 || !response.body().matches("\\s*\\{\\s*\"acknowledged\"\\s*:\\s*"+last+"\\s*}\\s*"))throw new IllegalStateException("API did not acknowledge batch (HTTP "+response.statusCode()+")");
                db.apiAcknowledge(last).get(10,TimeUnit.SECONDS);
            } catch(Exception error) {
                if(System.currentTimeMillis()-lastWarning>60000){lastWarning=System.currentTimeMillis();plugin.getLogger().warning("Reports API unavailable; queued changes retained for retry. Check endpoint/key and website status.");}
            }
        },5,10,TimeUnit.SECONDS);
    }
    @Override public void close(){worker.shutdownNow();}
}
