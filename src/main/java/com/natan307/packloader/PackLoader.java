package com.natan307.packloader;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class PackLoader extends JavaPlugin implements Listener {

    public static final String PREFIX = "§8[§bPackLoader§8] §7";

    private ItemRegistry items;
    private GuiRegistry guis;
    private GuiManager guiManager;
    private BlockManager blockManager;
    private PackBuilder builder;
    private PackServer server;
    private volatile BuiltPack pack;
    private volatile String cdnUrl;
    private final UUID packId = UUID.nameUUIDFromBytes("packloader:server-pack".getBytes(StandardCharsets.UTF_8));
    private final AtomicBoolean building = new AtomicBoolean(false);
    private final AtomicBoolean queued = new AtomicBoolean(false);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        installExamples();
        startMetrics();
        items = new ItemRegistry(this);
        guis = new GuiRegistry(this);
        builder = new PackBuilder(this);

        guiManager = new GuiManager(this);
        blockManager = new BlockManager(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(blockManager, this);

        PackCommand command = new PackCommand(this);
        PluginCommand pc = getCommand("packloader");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        startServer();
        rebuild(null, false);
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    /**
     * Anonymous usage statistics via bStats. Can be disabled with "metrics.enabled: false" in config.yml
     * or globally for all plugins in plugins/bStats/config.yml. No IP addresses, hostnames, player names
     * or file contents are collected by PackLoader.
     */
    private void startMetrics() {
        if (!getConfig().getBoolean("metrics.enabled", true)) {
            return;
        }
        try {
            final String cdnMode = getConfig().getString("cdn.mode", "none").toLowerCase(Locale.ROOT);
            final String optimizer = getConfig().getBoolean("optimize.enabled", true) ? "enabled" : "disabled";
            Metrics metrics = new Metrics(this, 35402);
            metrics.addCustomChart(new SimplePie("cdn_mode", () -> cdnMode));
            metrics.addCustomChart(new SimplePie("optimizer", () -> optimizer));
        } catch (Throwable t) {
            getLogger().fine("Metrics unavailable: " + t.getMessage());
        }
    }

    private void startServer() {
        try {
            PackServer s = new PackServer(this);
            s.start();
            server = s;
            getLogger().info("Pack server listening on " + getConfig().getString("http.bind-address", "0.0.0.0")
                    + ":" + getConfig().getInt("http.port", 8163)
                    + " (make sure this TCP port is reachable by players).");
            if (getConfig().getString("http.public-host", "").isBlank()) {
                getLogger().warning("http.public-host is empty - set it to your server's public IP/domain for reliable downloads.");
            }
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Could not start the pack web server (port in use, or the JVM lacks the "
                    + "jdk.httpserver module): " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ build

    public void rebuild(CommandSender requester, boolean resend) {
        if (!building.compareAndSet(false, true)) {
            queued.set(true);
            reply(requester, "A build is already running - another one is queued.");
            return;
        }
        reloadConfig();
        items.load();
        guis.load();
        final List<CustomItem> itemSnapshot = List.copyOf(items.all());
        final PackBuilder.Settings settings = new PackBuilder.Settings(
                getPackNamespace(),
                getPackFormat(),
                TextUtil.color(getConfig().getString("resource-pack.description", "PackLoader resource pack")),
                getConfig().getBoolean("optimize.enabled", true),
                getConfig().getBoolean("optimize.strip-png-metadata", true),
                List.copyOf(guis.all()));
        final CdnUploader.Settings cdn = CdnUploader.Settings.from(getConfig());
        reply(requester, "Building resource pack...");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                long start = System.currentTimeMillis();
                BuiltPack built = builder.build(itemSnapshot, settings);
                String publicUrl = CdnUploader.publish(cdn, built, getLogger());
                long took = System.currentTimeMillis() - start;
                if (!isEnabled()) {
                    return;
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    BuiltPack old = pack;
                    pack = built;
                    cdnUrl = publicUrl;
                    boolean changed = old == null || !Arrays.equals(old.sha1(), built.sha1());
                    String msg = "Pack ready: " + built.fileCount() + " files, "
                            + (built.size() / 1024) + " KB"
                            + (built.savedBytes() > 0 ? " (optimizer saved " + (built.savedBytes() / 1024) + " KB)" : "")
                            + ", sha1 " + built.sha1Hex().substring(0, 8)
                            + " (" + took + " ms)";
                    getLogger().info(msg);
                    reply(requester, msg);
                    if (built.size() > 250L * 1024 * 1024) {
                        getLogger().warning("The pack is larger than 250 MiB - Minecraft clients refuse server packs this big.");
                    }
                    if (resend && changed) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            sendPack(p);
                        }
                    }
                });
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Failed to build the resource pack", t);
                if (isEnabled()) {
                    Bukkit.getScheduler().runTask(this, () -> reply(requester, "§cBuild failed: " + t.getMessage()));
                }
            } finally {
                building.set(false);
                if (queued.getAndSet(false) && isEnabled()) {
                    Bukkit.getScheduler().runTask(this, () -> rebuild(null, true));
                }
            }
        });
    }

    /** Called from the HTTP thread after a successful upload. */
    public void onUploaded(String fileName) {
        getLogger().info("Pack uploaded via web: " + fileName);
        if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, () -> rebuild(null, true));
        }
    }

    private void reply(CommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage(PREFIX + message);
        }
    }

    // ---------------------------------------------------------------- sending

    public void sendPack(Player player) {
        BuiltPack bp = pack;
        if (bp == null) {
            return;
        }
        if (cdnUrl == null && server == null) {
            return;
        }
        String url = getPackUrl(player);
        boolean force = getConfig().getBoolean("send.force", false);
        String prompt = TextUtil.color(getConfig().getString("send.prompt", ""));
        String promptArg = prompt.isEmpty() ? null : prompt;
        try {
            player.setResourcePack(packId, url, bp.sha1(), promptArg, force);
        } catch (NoSuchMethodError err) {
            player.setResourcePack(url, bp.sha1(), promptArg, force);
        }
    }

    /** The URL players use: the CDN URL when one is configured and working, else the built-in server. */
    public String getPackUrl(Player viewer) {
        String cdn = cdnUrl;
        if (cdn != null) {
            return cdn;
        }
        BuiltPack bp = pack;
        return baseUrl(viewer) + "/pack.zip" + (bp == null ? "" : "?h=" + bp.sha1Hex());
    }

    /** Base URL (scheme://host[:port]) players should use to reach the built-in web server. */
    public String baseUrl(Player viewer) {
        String host = getConfig().getString("http.public-host", "").trim();
        boolean https = getConfig().getBoolean("http.public-https", false);
        int port = getConfig().getInt("http.public-port", 0);
        if (port <= 0) {
            port = getConfig().getInt("http.port", 8163);
        }
        if (host.isEmpty() && viewer != null) {
            host = virtualHost(viewer);
        }
        if (host.isEmpty()) {
            host = Bukkit.getIp();
        }
        if (host == null || host.isEmpty() || host.equals("0.0.0.0")) {
            host = guessLocalAddress();
        }
        boolean defaultPort = (!https && port == 80) || (https && port == 443);
        return (https ? "https://" : "http://") + host + (defaultPort ? "" : ":" + port);
    }

    private static String virtualHost(Player p) {
        try {
            Object o = p.getClass().getMethod("getVirtualHost").invoke(p);
            if (o instanceof InetSocketAddress addr && addr.getHostString() != null) {
                return addr.getHostString();
            }
        } catch (Throwable ignored) {
            // Spigot has no getVirtualHost
        }
        return "";
    }

    private static String guessLocalAddress() {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 53); // UDP connect only selects a route, nothing is sent
            String a = s.getLocalAddress().getHostAddress();
            if (a != null && !a.equals("0.0.0.0")) {
                return a;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "127.0.0.1";
    }

    // ----------------------------------------------------------------- events

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("send.on-join", true)) {
            return;
        }
        long delay = Math.max(1, getConfig().getLong("send.delay-ticks", 20L));
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) {
                sendPack(p);
            }
        }, delay);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        String status = event.getStatus().name();
        if (status.equals("FAILED_DOWNLOAD") || status.equals("INVALID_URL")) {
            getLogger().warning(event.getPlayer().getName() + " could not download the pack (" + status + "). "
                    + "Check that the URL is reachable from the player's network"
                    + (cdnUrl == null ? " and TCP port " + getConfig().getInt("http.port", 8163) + " is open" : "")
                    + ". URL: " + getPackUrl(event.getPlayer()));
        }
    }

    // ---------------------------------------------------------------- helpers

    public String getPackNamespace() {
        String ns = getConfig().getString("resource-pack.namespace", "packloader").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "");
        return ns.isEmpty() ? "packloader" : ns;
    }

    public int getPackFormat() {
        int configured = getConfig().getInt("resource-pack.pack-format", 0);
        if (configured > 0) {
            return configured;
        }
        String v = Bukkit.getBukkitVersion().split("-")[0];
        return switch (v) {
            case "1.21", "1.21.1" -> 34;
            case "1.21.2", "1.21.3" -> 42;
            case "1.21.4" -> 46;
            case "1.21.5" -> 55;
            case "1.21.6" -> 63;
            case "1.21.7", "1.21.8" -> 64;
            case "1.21.9", "1.21.10" -> 69;
            default -> 75; // newer versions are covered by the wide supported range in pack.mcmeta
        };
    }

    private void installExamples() {
        Path data = getDataFolder().toPath();
        if (Files.exists(data.resolve("packs"))) {
            return; // not a first run
        }
        copyResource("example/packs/example/assets/packloader/textures/item/ruby.png",
                data.resolve("packs/example/assets/packloader/textures/item/ruby.png"));
        copyResource("example/packs/example/assets/packloader/textures/gui/example_menu.png",
                data.resolve("packs/example/assets/packloader/textures/gui/example_menu.png"));
        copyResource("example/packs/example/assets/packloader/models/item/ruby_crystal.json",
                data.resolve("packs/example/assets/packloader/models/item/ruby_crystal.json"));
        copyResource("example/items/example.yml", data.resolve("items/example.yml"));
        copyResource("example/guis/example.yml", data.resolve("guis/example.yml"));
    }

    private void copyResource(String resource, Path target) {
        try (InputStream in = getResource(resource)) {
            if (in == null) {
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            getLogger().warning("Could not install example file " + resource + ": " + ex.getMessage());
        }
    }

    public ItemRegistry getItems() {
        return items;
    }

    public GuiRegistry getGuis() {
        return guis;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public PackServer getPackServer() {
        return server;
    }

    public BuiltPack getPack() {
        return pack;
    }

    public boolean usingCdn() {
        return cdnUrl != null;
    }
}
