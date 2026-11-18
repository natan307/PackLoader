package com.natan307.packloader;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.zip.ZipFile;

/** Tiny built-in web server: serves the generated pack and (optionally) a token-protected upload page. */
public final class PackServer {

    private static final String UPLOAD_PAGE = """
            <!doctype html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>PackLoader upload</title>
            <style>
              body{font-family:system-ui,sans-serif;max-width:520px;margin:3rem auto;padding:0 1rem;color:#222}
              button{padding:.5rem 1rem;margin-left:.5rem}
              pre{background:#f3f3f3;padding:.75rem;border-radius:6px;white-space:pre-wrap}
            </style></head>
            <body>
            <h2>PackLoader - upload resource pack</h2>
            <p>Choose a resource pack <b>.zip</b>. The server merges it automatically and resends the pack to players.</p>
            <input type="file" id="f" accept=".zip"><button id="b">Upload</button>
            <pre id="o">Waiting...</pre>
            <script>
            const o = document.getElementById('o');
            document.getElementById('b').onclick = async () => {
              const f = document.getElementById('f').files[0];
              if (!f) { o.textContent = 'Choose a .zip first'; return; }
              o.textContent = 'Uploading ' + f.name + ' ...';
              const base = location.pathname.endsWith('/') ? location.pathname : location.pathname + '/';
              try {
                const r = await fetch(base + encodeURIComponent(f.name), { method: 'PUT', body: f });
                o.textContent = r.status + ' ' + await r.text();
              } catch (e) { o.textContent = 'Failed: ' + e; }
            };
            </script>
            </body></html>
            """;

    private final PackLoader plugin;
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private HttpServer server;
    private ExecutorService executor;

    public PackServer(PackLoader plugin) {
        this.plugin = plugin;
    }

    public void start() throws IOException {
        String bind = plugin.getConfig().getString("http.bind-address", "0.0.0.0");
        int port = plugin.getConfig().getInt("http.port", 8163);

        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "PackLoader-HTTP");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    // ------------------------------------------------------------------ tokens

    public String createUploadToken() {
        long now = System.currentTimeMillis();
        tokens.values().removeIf(exp -> exp < now);
        byte[] raw = new byte[18];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long minutes = Math.max(1, plugin.getConfig().getLong("upload.token-expire-minutes", 15));
        tokens.put(token, now + minutes * 60_000L);
        return token;
    }

    private boolean validToken(String token) {
        Long exp = tokens.get(token);
        if (exp == null) {
            return false;
        }
        if (exp < System.currentTimeMillis()) {
            tokens.remove(token);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- handlers

    private void handle(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
            if (path.equals("/pack.zip")) {
                servePack(ex, method);
            } else if (path.startsWith("/upload/")) {
                handleUpload(ex, method, path);
            } else {
                send(ex, 404, "text/plain", "Not found");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "HTTP request failed: " + e.getMessage());
            try {
                send(ex, 500, "text/plain", "Internal error");
            } catch (Exception ignored) {
                // connection already gone
            }
        } finally {
            ex.close();
        }
    }

    private void servePack(HttpExchange ex, String method) throws IOException {
        BuiltPack pack = plugin.getPack();
        if (pack == null) {
            send(ex, 503, "text/plain", "Pack is still being built, try again in a moment.");
            return;
        }
        if (!method.equals("GET") && !method.equals("HEAD")) {
            send(ex, 405, "text/plain", "Method not allowed");
            return;
        }
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/zip");
        h.set("Content-Disposition", "attachment; filename=\"pack.zip\"");
        if (method.equals("HEAD")) {
            h.set("Content-Length", Long.toString(pack.size()));
            ex.sendResponseHeaders(200, -1);
            return;
        }
        ex.sendResponseHeaders(200, pack.size());
        try (InputStream in = Files.newInputStream(pack.file());
             OutputStream os = ex.getResponseBody()) {
            in.transferTo(os);
        }
    }

    private void handleUpload(HttpExchange ex, String method, String path) throws IOException {
        if (!plugin.getConfig().getBoolean("upload.enabled", true)) {
            send(ex, 403, "text/plain", "Uploads are disabled.");
            return;
        }
        String[] parts = path.substring("/upload/".length()).split("/", 2);
        String token = parts[0];
        if (!validToken(token)) {
            send(ex, 403, "text/plain", "Invalid or expired token. Run /packloader upload again.");
            return;
        }

        if (parts.length == 1 || parts[1].isEmpty()) {
            if (!method.equals("GET")) {
                send(ex, 405, "text/plain", "Method not allowed");
                return;
            }
            send(ex, 200, "text/html", UPLOAD_PAGE);
            return;
        }

        if (!method.equals("PUT") && !method.equals("POST")) {
            send(ex, 405, "text/plain", "Use PUT");
            return;
        }

        String fileName = sanitize(parts[1]);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            send(ex, 400, "text/plain", "Only .zip files are accepted.");
            return;
        }

        long max = Math.max(1, plugin.getConfig().getLong("upload.max-size-mb", 200)) * 1024L * 1024L;
        String declared = ex.getRequestHeaders().getFirst("Content-Length");
        if (declared != null) {
            try {
                if (Long.parseLong(declared.trim()) > max) {
                    send(ex, 413, "text/plain", "File too large (limit " + (max / 1024 / 1024) + " MB).");
                    return;
                }
            } catch (NumberFormatException ignored) {
                // fall through, size is counted while reading
            }
        }

        Path packsDir = plugin.getDataFolder().toPath().resolve("packs");
        Files.createDirectories(packsDir);
        Path tmp = packsDir.resolve("." + fileName + ".uploading");
        Path target = packsDir.resolve(fileName);

        boolean tooLarge = false;
        try (InputStream in = ex.getRequestBody(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > max) {
                    tooLarge = true;
                    break;
                }
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }

        if (tooLarge) {
            Files.deleteIfExists(tmp);
            send(ex, 413, "text/plain", "File too large (limit " + (max / 1024 / 1024) + " MB).");
            return;
        }

        try (ZipFile ignored = new ZipFile(tmp.toFile())) {
            // readable zip - ok
        } catch (IOException bad) {
            Files.deleteIfExists(tmp);
            send(ex, 400, "text/plain", "That file is not a valid zip.");
            return;
        }

        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        send(ex, 200, "text/plain", "Uploaded " + fileName + ". The pack is being rebuilt.");
        plugin.onUploaded(fileName);
    }

    private static String sanitize(String raw) {
        String name = raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.length() > 80) {
            String ext = name.toLowerCase(Locale.ROOT).endsWith(".zip") ? ".zip" : "";
            name = name.substring(0, 76) + ext;
        }
        return name.isEmpty() ? "upload.zip" : name;
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        }
    }
}
