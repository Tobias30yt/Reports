package de.example.reports.update;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks stable GitHub releases and stages verified JARs for Paper's next startup. */
public final class ReleaseUpdateChecker {
    private static final URI RELEASES = URI.create("https://api.github.com/repos/Tobias30yt/Reports/releases?per_page=100");
    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern PRERELEASE = Pattern.compile("\\\"prerelease\\\"\\s*:\\s*(true|false)");
    private static final Pattern ASSET_NAME = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ASSET_URL = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ASSET_DIGEST = Pattern.compile("\\\"digest\\\"\\s*:\\s*\\\"sha256:([0-9a-fA-F]{64})\\\"");
    private static final Pattern VERSION = Pattern.compile("(?m)^version:\\s*['\\\"]?([^'\\\"\\r\\n]+)");
    private static final Pattern NAME = Pattern.compile("(?m)^name:\\s*['\\\"]?([^'\\\"\\r\\n]+)");
    private static final long MAX_JAR_BYTES = 128L * 1024 * 1024;
    private final JavaPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private ScheduledTask periodic;
    private record Release(String tag, boolean prerelease, String json) {}

    public ReleaseUpdateChecker(JavaPlugin plugin) { this.plugin = plugin; }
    public void start() {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> check());
        periodic = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> check(), 48, 48, TimeUnit.HOURS);
    }

    private void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder(RELEASES).timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Reports/" + plugin.getPluginMeta().getVersion()).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("GitHub returned HTTP " + response.statusCode());
            List<Release> releases = parseReleases(response.body());
            if (releases.isEmpty()) throw new IllegalStateException("No release tags in GitHub response");
            String installed = plugin.getPluginMeta().getVersion();
            Release latestRelease = releases.stream().filter(release -> !release.prerelease()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No stable GitHub release found"));
            String latest = latestRelease.tag();
            int behind = (int) releases.stream().filter(release -> !release.prerelease())
                    .filter(release -> compare(release.tag(), installed) > 0).count();
            if (behind == 0) return;
            Asset asset = findAsset(latestRelease, latest);
            String download = asset.url();
            stageUpdate(download, asset.digest(), latest);
            notifyStaff("Reports " + latest + " downloaded and staged. Paper will install it on the next server restart.");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not check or stage Reports updates: " + e.getMessage());
        }
    }

    private void stageUpdate(String download, String expectedDigest, String latest) throws Exception {
        Path plugins = plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
        Path running = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        if (plugins == null || !plugins.equals(running.getParent()) || !Files.isRegularFile(running)
                || !running.getFileName().toString().toLowerCase().endsWith(".jar"))
            throw new IllegalStateException("Reports is not loaded from a JAR directly inside the plugins folder");
        Path updateDir = Bukkit.getUpdateFolderFile().toPath().toAbsolutePath().normalize();
        Files.createDirectories(updateDir);
        Path staged = updateDir.resolve(running.getFileName());
        String stagedVersion = jarVersion(staged);
        if (stagedVersion != null && compare(stagedVersion, latest) >= 0) {
            plugin.getLogger().info("Reports " + stagedVersion + " is already staged for the next server restart.");
            return;
        }
        Path temporary = Files.createTempFile(updateDir, "reports-update-", ".part");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(download)).timeout(Duration.ofMinutes(2))
                    .header("User-Agent", "Reports-Updater").GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) throw new IllegalStateException("JAR download returned HTTP " + response.statusCode());
                if (response.headers().firstValueAsLong("Content-Length").orElse(-1) > MAX_JAR_BYTES)
                    throw new IllegalStateException("Reports JAR exceeds the 128 MiB download limit");
                try (var out = Files.newOutputStream(temporary)) {
                    byte[] buffer = new byte[8192]; long total = 0;
                    for (int n; (n = in.read(buffer)) >= 0;) {
                        total += n;
                        if (total > MAX_JAR_BYTES) throw new IllegalStateException("Reports JAR exceeds the 128 MiB download limit");
                        digest.update(buffer, 0, n);
                        out.write(buffer, 0, n);
                    }
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedDigest))
                throw new IllegalStateException("Downloaded Reports JAR failed GitHub SHA-256 verification");
            String downloadedVersion = jarVersion(temporary);
            if (downloadedVersion == null || compare(latest, downloadedVersion) != 0)
                throw new IllegalStateException("Downloaded Reports JAR is invalid or its version does not match " + latest);
            try { Files.move(temporary, staged, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, staged, StandardCopyOption.REPLACE_EXISTING); }
            plugin.getLogger().info("Downloaded Reports " + latest + " to plugins/update/" + running.getFileName()
                    + "; Paper will install it on the next restart.");
        } finally { Files.deleteIfExists(temporary); }
    }

    private String jarVersion(Path path) throws Exception {
        if (!Files.isRegularFile(path)) return null;
        try (JarFile file = new JarFile(path.toFile())) {
            var descriptor = file.getJarEntry("plugin.yml");
            if (descriptor == null) return null;
            String yaml;
            try (InputStream in = file.getInputStream(descriptor)) { yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            Matcher name = NAME.matcher(yaml), version = VERSION.matcher(yaml);
            if (!name.find() || !name.group(1).trim().equals("Reports") || !version.find()) return null;
            return version.group(1).trim();
        }
    }

    private record Asset(String url, String digest) {}
    private Asset findAsset(Release release, String latest) {
        for (String object : jsonObjects(release.json())) {
            Matcher name = ASSET_NAME.matcher(object), url = ASSET_URL.matcher(object), digest = ASSET_DIGEST.matcher(object);
            if (!name.find() || !url.find() || !digest.find() || !name.group(1).equals("Reports.jar")) continue;
            String candidate = url.group(1);
            if (candidate.contains("/download/" + latest + "/")
                    && candidate.matches("https://github\\.com/Tobias30yt/Reports/releases/download/[^/]+/Reports\\.jar"))
                return new Asset(candidate, digest.group(1));
        }
        throw new IllegalStateException("Latest Reports release has no Reports.jar with a SHA-256 digest");
    }

    private static List<Release> parseReleases(String json) {
        List<Release> releases = new ArrayList<>();
        for (String object : jsonObjects(json)) {
            Matcher tag = TAG.matcher(object), prerelease = PRERELEASE.matcher(object);
            if (tag.find() && prerelease.find()) releases.add(new Release(tag.group(1), Boolean.parseBoolean(prerelease.group(1)), object));
        }
        return releases;
    }

    private static List<String> jsonObjects(String json) {
        List<String> objects = new ArrayList<>();
        int depth = 0, start = -1;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}' && depth > 0 && --depth == 0) objects.add(json.substring(start, i + 1));
        }
        return objects;
    }

    private void notifyStaff(String message) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            plugin.getLogger().warning(message);
            for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) if (player.isOp())
                player.getScheduler().run(plugin, playerTask -> de.example.reports.language.Language.send(player,message), null);
        });
    }

    private static int compare(String a, String b) {
        List<Integer> x = parts(a), y = parts(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            int l = i < x.size() ? x.get(i) : 0, r = i < y.size() ? y.get(i) : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }
    private static List<Integer> parts(String value) {
        Matcher matcher = Pattern.compile("\\d+").matcher(value);
        List<Integer> out = new ArrayList<>();
        while (matcher.find()) try { out.add(Integer.parseInt(matcher.group())); }
        catch (NumberFormatException ignored) { out.add(Integer.MAX_VALUE); }
        return out;
    }
    public void shutdown() { if (periodic != null) periodic.cancel(); }
}
