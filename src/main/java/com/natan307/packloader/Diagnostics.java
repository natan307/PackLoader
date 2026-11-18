package com.natan307.packloader;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Support report: gathers the information that is needed to debug a problem and writes it to
 * plugins/PackLoader/diag.txt. Secrets (CDN upload URL and headers) are never included.
 */
final class Diagnostics {

    private Diagnostics() {
    }

    /** Blocking (does a network self-test) - call from an async thread. */
    static Path write(PackLoader plugin) throws IOException {
        List<String> out = new ArrayList<>();
        out.add("PackLoader diagnostic report - " + Instant.now());
        out.add("Version: " + plugin.getDescription().getVersion());
        out.add("Server: " + Bukkit.getVersion());
        out.add("Bukkit API: " + Bukkit.getBukkitVersion());
        out.add("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        out.add("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        out.add("Online mode: " + Bukkit.getOnlineMode());
        out.add("");

        BuiltPack pack = plugin.getPack();
        if (pack == null) {
            out.add("Pack: not built yet");
        } else {
            out.add("Pack: " + pack.fileCount() + " files, " + pack.size() + " bytes, sha1 " + pack.sha1Hex()
                    + ", optimizer saved " + pack.savedBytes() + " bytes");
        }
        out.add("Items: " + plugin.getItems().ids().size() + "  GUIs: " + plugin.getGuis().ids().size());
        out.add("Web server running: " + (plugin.getPackServer() != null && plugin.getPackServer().isRunning()));
        out.add("Using CDN: " + plugin.usingCdn());
        String url = plugin.getPackUrl(null);
        out.add("Pack URL: " + url);
        out.add("Self-test: " + selfTest(url));
        out.add("");

        out.add("Config (secrets removed):");
        FileConfiguration cfg = plugin.getConfig();
        for (String key : cfg.getKeys(true)) {
            if (cfg.isConfigurationSection(key)) {
                continue;
            }
            String lower = key.toLowerCase();
            if (lower.startsWith("cdn.upload-url") || lower.startsWith("cdn.upload-headers")) {
                out.add("  " + key + ": <hidden>");
            } else {
                out.add("  " + key + ": " + cfg.get(key));
            }
        }
        out.add("");

        out.add("Other plugins:");
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            out.add("  " + p.getName() + " " + p.getDescription().getVersion());
        }

        Path file = plugin.getDataFolder().toPath().resolve("diag.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, out, StandardCharsets.UTF_8);
        return file;
    }

    /** Requests the pack URL (HEAD) from this machine. Passing here does not prove players can reach it. */
    private static String selfTest(String url) {
        if (url == null || url.isBlank()) {
            return "no URL";
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return "HTTP " + resp.statusCode() + " (reachable from this machine; "
                    + "players on other networks may still be blocked by a firewall / closed port)";
        } catch (Exception ex) {
            return "FAILED: " + ex.getClass().getSimpleName() + ": " + ex.getMessage()
                    + " (port closed, wrong public-host, or the hosting blocks loopback connections)";
        }
    }
}
