package com.natan307.packloader;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ItemRegistry {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    private final PackLoader plugin;
    private final NamespacedKey idKey;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();
    private boolean warnedItemModel;

    public ItemRegistry(PackLoader plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item_id");
    }

    public void load() {
        items.clear();
        Path dir = plugin.getDataFolder().toPath().resolve("items");
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
            plugin.getLogger().warning("Could not read items folder: " + ex.getMessage());
            return;
        }

        for (Path file : files) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection root = cfg.getConfigurationSection("items");
            if (root == null) {
                continue;
            }
            for (String rawId : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(rawId);
                if (s == null) {
                    continue;
                }
                CustomItem item = parse(file.getFileName().toString(), rawId, s);
                if (item != null) {
                    if (items.containsKey(item.id())) {
                        plugin.getLogger().warning("Duplicate item id '" + item.id() + "' in "
                                + file.getFileName() + " - overriding the earlier one.");
                    }
                    items.put(item.id(), item);
                }
            }
        }
        long blocks = items.values().stream().filter(i -> i.block() != null).count();
        plugin.getLogger().info("Loaded " + items.size() + " custom item(s), " + blocks + " placeable block(s).");
    }

    private CustomItem parse(String fileName, String rawId, ConfigurationSection s) {
        String id = rawId.toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            plugin.getLogger().warning(fileName + ": invalid item id '" + rawId + "' (use a-z, 0-9, _ only).");
            return null;
        }
        Material material = Material.matchMaterial(s.getString("material", "PAPER"));
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning(fileName + ": item '" + id + "' has an invalid material.");
            return null;
        }

        String ns = plugin.getPackNamespace();
        String explicit = norm(s.getString("item_model"), ns);
        String model = norm(s.getString("model"), ns);
        String texture = norm(stripPng(s.getString("texture")), ns);

        String itemModel;
        boolean generate;
        if (explicit != null) {
            itemModel = explicit;
            generate = false;
        } else if (model != null || texture != null) {
            itemModel = ns + ":" + id;
            generate = true;
        } else {
            itemModel = null;
            generate = false;
        }

        Integer cmd = s.contains("custom_model_data") ? s.getInt("custom_model_data") : null;

        CustomItem.BlockDef block = null;
        ConfigurationSection bs = s.getConfigurationSection("block");
        if (bs != null) {
            block = new CustomItem.BlockDef(
                    (float) Math.max(0.05, bs.getDouble("scale", 1.0)),
                    Math.max(0, bs.getInt("rotation", 90)),
                    bs.getString("transform", "NONE").toUpperCase(Locale.ROOT),
                    bs.getBoolean("glow", false),
                    bs.getString("sound", "stone").toLowerCase(Locale.ROOT));
        } else if (s.isBoolean("block") && s.getBoolean("block")) {
            block = new CustomItem.BlockDef(1.0f, 90, "NONE", false, "stone");
        }
        if (block != null && itemModel == null) {
            plugin.getLogger().warning(fileName + ": block '" + id + "' has no model/texture/item_model - it will be invisible.");
        }

        return new CustomItem(
                id,
                s.getString("display_name"),
                s.getStringList("lore"),
                material,
                itemModel,
                generate,
                model,
                texture,
                s.getString("parent", "generated"),
                cmd,
                s.getBoolean("glint", false),
                s.getBoolean("unbreakable", false),
                s.getBoolean("hide_flags", false),
                block);
    }

    private static String stripPng(String v) {
        if (v == null) {
            return null;
        }
        return v.toLowerCase(Locale.ROOT).endsWith(".png") ? v.substring(0, v.length() - 4) : v;
    }

    private static String norm(String v, String ns) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.contains(":") ? s : ns + ":" + s;
    }

    public Collection<CustomItem> all() {
        return items.values();
    }

    public Set<String> ids() {
        return items.keySet();
    }

    public CustomItem get(String id) {
        return id == null ? null : items.get(id.toLowerCase(Locale.ROOT));
    }

    public ItemStack create(String id, int amount) {
        CustomItem ci = get(id);
        if (ci == null) {
            return null;
        }
        ItemStack stack = new ItemStack(ci.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        if (ci.displayName() != null) {
            meta.setDisplayName(ChatColor.RESET + TextUtil.color(ci.displayName()));
        }
        if (!ci.lore().isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : ci.lore()) {
                lore.add(ChatColor.RESET + TextUtil.color(line));
            }
            meta.setLore(lore);
        }
        if (ci.itemModel() != null) {
            applyItemModel(meta, ci.itemModel());
        }
        if (ci.customModelData() != null) {
            meta.setCustomModelData(ci.customModelData());
        }
        if (ci.glint()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (ci.unbreakable()) {
            meta.setUnbreakable(true);
        }
        if (ci.hideFlags()) {
            meta.addItemFlags(ItemFlag.values());
        }
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, ci.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private void applyItemModel(ItemMeta meta, String model) {
        NamespacedKey key = NamespacedKey.fromString(model);
        if (key == null) {
            plugin.getLogger().warning("Invalid item model key: " + model);
            return;
        }
        try {
            meta.setItemModel(key);
        } catch (NoSuchMethodError err) {
            if (!warnedItemModel) {
                warnedItemModel = true;
                plugin.getLogger().warning("This server version has no item_model support (needs 1.21.2+; "
                        + "generated 3D items need 1.21.4+). Items are given without a custom model.");
            }
        }
    }

    /** Returns the PackLoader item id of a stack, or null. */
    public String idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }
}
