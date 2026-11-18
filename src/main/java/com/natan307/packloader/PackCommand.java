package com.natan307.packloader;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PackCommand implements CommandExecutor, TabCompleter {

    private static final String P = PackLoader.PREFIX;
    private static final List<String> SUBS = List.of("reload", "send", "give", "list", "url", "upload", "info", "gui", "browser", "diag");

    private final PackLoader plugin;

    public PackCommand(PackLoader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("packloader.admin")) {
            sender.sendMessage(P + "§cYou do not have permission.");
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> plugin.rebuild(sender, true);
            case "send" -> send(sender, args);
            case "give" -> give(sender, args);
            case "list" -> list(sender);
            case "url" -> url(sender);
            case "upload" -> upload(sender);
            case "info" -> info(sender);
            case "gui" -> gui(sender, args);
            case "browser" -> browser(sender);
            case "diag" -> diag(sender);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        s.sendMessage(P + "Commands:");
        s.sendMessage("§b/" + label + " reload §7- rebuild the pack and resend it");
        s.sendMessage("§b/" + label + " send [player|all] §7- send the pack");
        s.sendMessage("§b/" + label + " give <player> <item> [amount] §7- give a custom item");
        s.sendMessage("§b/" + label + " list §7- list custom items");
        s.sendMessage("§b/" + label + " upload §7- temporary web page to upload a pack .zip");
        s.sendMessage("§b/" + label + " url §7- show the download URL");
        s.sendMessage("§b/" + label + " info §7- pack information");
        s.sendMessage("§7Support the author: §fhttps://ko-fi.com/natanezra");
    }

    private void send(CommandSender sender, String[] args) {
        if (plugin.getPack() == null) {
            sender.sendMessage(P + "The pack is not built yet.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.sendPack(p);
            }
            sender.sendMessage(P + "Pack sent to all players.");
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else {
            target = sender instanceof Player p ? p : null;
        }
        if (target == null) {
            sender.sendMessage(P + "§cPlayer not found. Usage: /packloader send [player|all]");
            return;
        }
        plugin.sendPack(target);
        sender.sendMessage(P + "Pack sent to " + target.getName() + ".");
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(P + "§cUsage: /packloader give <player> <item> [amount]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(P + "§cPlayer not found.");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(2304, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(P + "§cAmount must be a number.");
                return;
            }
        }
        ItemStack stack = plugin.getItems().create(args[2], amount);
        if (stack == null) {
            sender.sendMessage(P + "§cUnknown item '" + args[2] + "'. Use /packloader list.");
            return;
        }
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
        sender.sendMessage(P + "Gave " + amount + "x " + args[2].toLowerCase(Locale.ROOT) + " to " + target.getName() + ".");
    }

    private void list(CommandSender sender) {
        Collection<String> ids = plugin.getItems().ids();
        if (ids.isEmpty()) {
            sender.sendMessage(P + "No custom items. Add .yml files to plugins/PackLoader/items/.");
            return;
        }
        sender.sendMessage(P + "Items (" + ids.size() + "): §f" + String.join("§7, §f", ids));
    }

    private void url(CommandSender sender) {
        Player viewer = sender instanceof Player p ? p : null;
        sender.sendMessage(P + "Pack URL: §f" + plugin.getPackUrl(viewer));
    }

    private void upload(CommandSender sender) {
        PackServer server = plugin.getPackServer();
        if (server == null || !plugin.getConfig().getBoolean("upload.enabled", true)) {
            sender.sendMessage(P + "§cUploads are disabled or the web server is not running.");
            return;
        }
        Player viewer = sender instanceof Player p ? p : null;
        String base = plugin.baseUrl(viewer);
        String token = server.createUploadToken();
        long minutes = plugin.getConfig().getLong("upload.token-expire-minutes", 15);
        sender.sendMessage(P + "Open this page in your browser (valid " + minutes + " min):");
        sender.sendMessage("§f" + base + "/upload/" + token);
        sender.sendMessage("§7Or from a terminal: §fcurl -T mypack.zip " + base + "/upload/" + token + "/mypack.zip");
    }

    private void info(CommandSender sender) {
        BuiltPack bp = plugin.getPack();
        if (bp == null) {
            sender.sendMessage(P + "The pack is not built yet.");
            return;
        }
        sender.sendMessage(P + "Files: §f" + bp.fileCount()
                + "§7  Size: §f" + (bp.size() / 1024) + " KB"
                + "§7  Items: §f" + plugin.getItems().ids().size());
        sender.sendMessage(P + "SHA-1: §f" + bp.sha1Hex());
    }

    private void gui(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(P + "\u00a7cUsage: /packloader gui <id> [player]. GUIs: \u00a7f"
                    + String.join(", ", plugin.getGuis().ids()));
            return;
        }
        GuiDef def = plugin.getGuis().get(args[1]);
        if (def == null) {
            sender.sendMessage(P + "\u00a7cUnknown GUI '" + args[1] + "'.");
            return;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(P + "\u00a7cPlayer not found. Usage: /packloader gui <id> <player>");
            return;
        }
        plugin.getGuiManager().open(target, def);
    }

    private void browser(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(P + "\u00a7cOnly players can open the browser.");
            return;
        }
        plugin.getGuiManager().openBrowser(p, 0);
    }

    private void diag(CommandSender sender) {
        sender.sendMessage(P + "Collecting diagnostics...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Path file = Diagnostics.write(plugin);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(P + "Report saved: \u00a7f"
                        + file.toString().replace('\\', '/') + "\u00a77 - send this file when asking for support."));
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(P + "\u00a7cFailed: " + ex.getMessage()));
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("packloader.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("send") || sub.equals("give"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            if (sub.equals("send")) {
                names.add("all");
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && sub.equals("give")) {
            return filter(plugin.getItems().ids(), args[2]);
        }
        if (args.length == 2 && sub.equals("gui")) {
            return filter(plugin.getGuis().ids(), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(o);
            }
        }
        return out;
    }
}
