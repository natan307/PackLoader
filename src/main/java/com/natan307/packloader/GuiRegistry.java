package com.natan307.packloader;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class GuiRegistry {

    private final PackLoader plugin;
    private final Map<String, GuiDef> guis = new LinkedHashMap<>();

    public GuiRegistry(PackLoader plugin) {
        this.plugin = plugin;
    }

    public void load() {
        guis.clear();
        Path dir = plugin.getDataFolder().toPath().resolve("guis");
        List<Path> files;
        try {
            Files.createDirectories(dir);
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return !n.startsWith(".") && (n.endsWith(".yml") || n.endsWith(".yaml"));
                        })
                        .sorted()
                        .toList();
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read guis folder: " + ex.getMessage());
            return;
        }

        int index = 0;
        for (Path file : files) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection root = cfg.getConfigurationSection("guis");
            if (root == null) {
                continue;
            }
            for (String rawId : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(rawId);
                if (s == null) {
                    continue;
                }
                String id = rawId.toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9_]+")) {
                    plugin.getLogger().warning(file.getFileName() + ": invalid gui id '" + rawId + "' (use a-z, 0-9, _).");
                    continue;
                }
                if (index >= 4000) {
                    plugin.getLogger().warning("Too many GUIs, ignoring '" + id + "'.");
                    continue;
                }
                guis.put(id, parse(id, s, (char) (0xE000 + index)));
                index++;
            }
        }
        plugin.getLogger().info("Loaded " + guis.size() + " custom GUI(s).");
    }

    private GuiDef parse(String id, ConfigurationSection s, char glyph) {
        int rows = Math.max(1, Math.min(6, s.getInt("rows", 3)));
        String background = normalize(s.getString("background"));

        Map<Integer, GuiDef.Button> buttons = new LinkedHashMap<>();
        ConfigurationSection bsec = s.getConfigurationSection("buttons");
        if (bsec != null) {
            for (String key : bsec.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(key.trim());
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("GUI '" + id + "': button slot '" + key + "' is not a number.");
                    continue;
                }
                if (slot < 0 || slot >= rows * 9) {
                    plugin.getLogger().warning("GUI '" + id + "': slot " + slot + " is outside the " + rows + " row(s).");
                    continue;
                }
                ConfigurationSection b = bsec.getConfigurationSection(key);
                if (b == null) {
                    continue;
                }
                Material mat = Material.matchMaterial(b.getString("material", "STONE"));
                if (mat == null || !mat.isItem()) {
                    mat = Material.STONE;
                }
                buttons.put(slot, new GuiDef.Button(
                        b.getString("item"),
                        mat,
                        b.getString("name"),
                        b.getStringList("lore"),
                        b.getStringList("commands")));
            }
        }

        return new GuiDef(
                id,
                s.getString("title", ""),
                rows,
                background,
                s.getInt("offset_x", 0),
                s.getInt("offset_y", 0),
                s.getBoolean("hide_title", false),
                glyph,
                buttons);
    }

    private String normalize(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith(".png")) {
            s = s.substring(0, s.length() - 4);
        }
        return s.contains(":") ? s : plugin.getPackNamespace() + ":" + s;
    }

    public Collection<GuiDef> all() {
        return guis.values();
    }

    public Set<String> ids() {
        return guis.keySet();
    }

    public GuiDef get(String id) {
        return id == null ? null : guis.get(id.toLowerCase(Locale.ROOT));
    }
}
