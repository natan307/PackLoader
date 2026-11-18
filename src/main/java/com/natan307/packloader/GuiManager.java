package com.natan307.packloader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Custom GUIs (with optional texture background) and the built-in item browser. */
public final class GuiManager implements Listener {

    static final class GuiHolder implements InventoryHolder {
        final GuiDef def;
        Inventory inventory;

        GuiHolder(GuiDef def) {
            this.def = def;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static final class BrowserHolder implements InventoryHolder {
        final int page;
        final int pages;
        Inventory inventory;

        BrowserHolder(int page, int pages) {
            this.page = page;
            this.pages = pages;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int BROWSER_ITEMS = 45;

    private final PackLoader plugin;
    private final boolean adventure;

    public GuiManager(PackLoader plugin) {
        this.plugin = plugin;
        boolean ok;
        try {
            Class.forName("net.kyori.adventure.text.Component");
            Class.forName("com.natan307.packloader.PaperTitles");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        this.adventure = ok;
    }

    // ------------------------------------------------------------ custom GUIs

    public void open(Player player, GuiDef def) {
        int size = def.rows() * 9;
        String title = TextUtil.color(def.title());
        GuiHolder holder = new GuiHolder(def);

        BuiltPack pack = plugin.getPack();
        Integer advance = (pack != null && def.background() != null) ? pack.glyphAdvances().get(def.id()) : null;

        Inventory inv = null;
        if (adventure) {
            try {
                String glyphs = "";
                if (advance != null) {
                    glyphs = Spacing.shift(-8 + def.offsetX()) + def.glyph() + Spacing.shift(8 - advance - def.offsetX());
                }
                inv = PaperTitles.create(holder, size, plugin.getPackNamespace(), glyphs, def.hideTitle() ? "" : title);
            } catch (Throwable t) {
                inv = null;
            }
        }
        if (inv == null) {
            inv = Bukkit.createInventory(holder, size, def.hideTitle() ? " " : title);
        }
        holder.inventory = inv;

        for (Map.Entry<Integer, GuiDef.Button> e : def.buttons().entrySet()) {
            inv.setItem(e.getKey(), buildButton(e.getValue()));
        }
        player.openInventory(inv);
    }

    private ItemStack buildButton(GuiDef.Button b) {
        ItemStack stack = null;
        if (b.item() != null) {
            stack = plugin.getItems().create(b.item(), 1);
        }
        if (stack == null) {
            stack = new ItemStack(b.item() != null ? Material.BARRIER : b.material());
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (b.name() != null) {
                meta.setDisplayName(ChatColor.RESET + TextUtil.color(b.name()));
            }
            if (!b.lore().isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String line : b.lore()) {
                    lore.add(ChatColor.RESET + TextUtil.color(line));
                }
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void run(Player p, List<String> commands) {
        for (String raw : commands) {
            String c = raw.trim().replace("%player%", p.getName());
            String lower = c.toLowerCase(Locale.ROOT);
            if (lower.startsWith("[close]")) {
                Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            } else if (lower.startsWith("[player]")) {
                p.performCommand(arg(c, 8));
            } else if (lower.startsWith("[console]")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), arg(c, 9));
            } else if (lower.startsWith("[message]")) {
                p.sendMessage(TextUtil.color(c.substring(9).trim()));
            } else if (lower.startsWith("[open]")) {
                GuiDef target = plugin.getGuis().get(c.substring(6).trim());
                if (target != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> open(p, target));
                }
            } else if (lower.startsWith("[give]")) {
                ItemStack st = plugin.getItems().create(c.substring(6).trim(), 1);
                if (st != null) {
                    giveOrDrop(p, st);
                }
            }
        }
    }

    private static String arg(String command, int prefixLength) {
        String s = command.substring(prefixLength).trim();
        return s.startsWith("/") ? s.substring(1) : s;
    }

    // ---------------------------------------------------------------- browser

    public void openBrowser(Player player, int page) {
        List<String> ids = new ArrayList<>(plugin.getItems().ids());
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) BROWSER_ITEMS));
        int current = Math.max(0, Math.min(page, pages - 1));

        BrowserHolder holder = new BrowserHolder(current, pages);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_GRAY + "PackLoader items " + ChatColor.GRAY + "(" + (current + 1) + "/" + pages + ")");
        holder.inventory = inv;

        for (int i = 0; i < BROWSER_ITEMS; i++) {
            int idx = current * BROWSER_ITEMS + i;
            if (idx >= ids.size()) {
                break;
            }
            ItemStack st = plugin.getItems().create(ids.get(idx), 1);
            if (st != null) {
                inv.setItem(i, st);
            }
        }
        if (current > 0) {
            inv.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        }
        inv.setItem(49, named(Material.BARRIER, ChatColor.RED + "Close"));
        if (current < pages - 1) {
            inv.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Next page"));
        }
        player.openInventory(inv);
    }

    private static ItemStack named(Material material, String name) {
        ItemStack st = new ItemStack(material);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            st.setItemMeta(meta);
        }
        return st;
    }

    private static void giveOrDrop(Player p, ItemStack stack) {
        for (ItemStack rest : p.getInventory().addItem(stack).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ----------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof GuiHolder) && !(holder instanceof BrowserHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getWhoClicked();

        if (holder instanceof GuiHolder gui) {
            GuiDef.Button b = gui.def.buttons().get(event.getSlot());
            if (b != null) {
                run(p, b.commands());
            }
            return;
        }

        BrowserHolder browser = (BrowserHolder) holder;
        if (!p.hasPermission("packloader.admin")) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 45 && browser.page > 0) {
            int target = browser.page - 1;
            Bukkit.getScheduler().runTask(plugin, () -> openBrowser(p, target));
        } else if (slot == 53 && browser.page < browser.pages - 1) {
            int target = browser.page + 1;
            Bukkit.getScheduler().runTask(plugin, () -> openBrowser(p, target));
        } else if (slot == 49) {
            Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
        } else if (slot < BROWSER_ITEMS) {
            ItemStack item = top.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return;
            }
            ItemStack give = item.clone();
            give.setAmount(event.isShiftClick() ? Math.max(1, item.getMaxStackSize()) : 1);
            giveOrDrop(p, give);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof GuiHolder) && !(holder instanceof BrowserHolder)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
