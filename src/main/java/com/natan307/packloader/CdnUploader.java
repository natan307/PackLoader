package com.natan307.packloader;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Optional CDN support.
 * <ul>
 *   <li>mode "url": players download from a fixed/templated URL (you upload the file yourself).</li>
 *   <li>mode "upload": after each build the pack is uploaded with an HTTP PUT/POST
 *       (works with Bunny Storage, WebDAV, pre-signed S3/R2 URLs, custom endpoints...).</li>
 * </ul>
 * Placeholders in URLs: {hash} = SHA-1 hex, {file} = file name of the pack.
 */
final class CdnUploader {

    record Settings(String mode, String publicUrl, String uploadUrl, String method, Map<String, String> headers) {

        static Settings from(FileConfiguration cfg) {
            Map<String, String> headers = new LinkedHashMap<>();
            ConfigurationSection h = cfg.getConfigurationSection("cdn.upload-headers");
            if (h != null) {
                for (String k : h.getKeys(false)) {
                    headers.put(k, String.valueOf(h.get(k)));
                }
            }
            return new Settings(
                    cfg.getString("cdn.mode", "none").toLowerCase(Locale.ROOT).trim(),
                    cfg.getString("cdn.public-url", ""),
                    cfg.getString("cdn.upload-url", ""),
                    cfg.getString("cdn.upload-method", "PUT").toUpperCase(Locale.ROOT).trim(),
                    headers);
        }
    }

    private static volatile HttpClient client;

    private CdnUploader() {
    }

    private static HttpClient client() {
        HttpClient c = client;
        if (c == null) {
            c = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            client = c;
        }
        return c;
    }

    private static String fill(String template, BuiltPack pack) {
        return template
                .replace("{hash}", pack.sha1Hex())
                .replace("{file}", pack.file().getFileName().toString());
    }

    /**
     * Publishes the pack according to the settings. Blocking - call from an async thread.
     *
     * @return the URL players should use, or null to fall back to the built-in server
     */
    static String publish(Settings s, BuiltPack pack, Logger log) {
        if (s.mode().equals("none") || s.mode().isEmpty()) {
            return null;
        }
        if (s.publicUrl().isBlank()) {
            log.warning("cdn.mode is '" + s.mode() + "' but cdn.public-url is empty - using the built-in server.");
            return null;
        }
        String publicUrl = fill(s.publicUrl(), pack);

        if (s.mode().equals("url")) {
            log.info("CDN (url mode): players download from " + publicUrl
                    + " - make sure " + pack.file().getFileName() + " is uploaded there.");
            return publicUrl;
        }
        if (!s.mode().equals("upload")) {
            log.warning("Unknown cdn.mode '" + s.mode() + "' (use none, url or upload).");
            return null;
        }
        if (s.uploadUrl().isBlank()) {
            log.warning("cdn.mode is 'upload' but cdn.upload-url is empty - using the built-in server.");
            return null;
        }
        if (!s.method().equals("PUT") && !s.method().equals("POST")) {
            log.warning("cdn.upload-method must be PUT or POST - using the built-in server.");
            return null;
        }

        try {
            String target = fill(s.uploadUrl(), pack);
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofMinutes(10))
                    .header("Content-Type", "application/zip");
            for (Map.Entry<String, String> h : s.headers().entrySet()) {
                rb.header(h.getKey(), h.getValue());
            }
            rb.method(s.method(), HttpRequest.BodyPublishers.ofFile(pack.file()));
            long start = System.currentTimeMillis();
            HttpResponse<String> resp = client().send(rb.build(), HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code / 100 == 2) {
                log.info("Pack uploaded to CDN (HTTP " + code + ", " + (System.currentTimeMillis() - start) + " ms). Players use "
                        + publicUrl);
                return publicUrl;
            }
            String body = resp.body() == null ? "" : resp.body();
            if (body.length() > 200) {
                body = body.substring(0, 200);
            }
            log.warning("CDN upload failed: HTTP " + code + " " + body + " - falling back to the built-in server.");
        } catch (Exception ex) {
            log.warning("CDN upload failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage()
                    + " - falling back to the built-in server.");
        }
        return null;
    }
}
