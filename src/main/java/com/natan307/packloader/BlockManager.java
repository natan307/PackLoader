package com.natan307.packloader;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Custom 3D blocks: an invisible BARRIER block provides collision and hit-box, an ItemDisplay
 * entity renders the model. Mining is instant (left click). No pack changes are needed -
 * the block uses the same item model as the item.
 */
public final class BlockManager implements Listener {

    private final PackLoader plugin;
    private final NamespacedKey key;
    private boolean internalBreak;

    public BlockManager(PackLoader plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "block_id");
    }

    // ------------------------------------------------------------- lookups

    private ItemDisplay findDisplay(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity e : block.getWorld().getNearbyEntities(center, 0.4, 0.4, 0.4)) {
            if (e instanceof ItemDisplay d && d.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                return d;
            }
        }
        return null;
    }

    // -------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            leftClick(event, clicked);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() == EquipmentSlot.HAND) {
            tryPlace(event, clicked);
        }
    }

    private void leftClick(PlayerInteractEvent event, Block clicked) {
        if (clicked.getType() != Material.BARRIER) {
            return;
        }
        ItemDisplay display = findDisplay(clicked);
        if (display == null) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        breakBlock(p, clicked, display);
    }

    private void tryPlace(PlayerInteractEvent event, Block clicked) {
        ItemStack hand = event.getItem();
        String id = plugin.getItems().idOf(hand);
        if (id == null) {
            return;
        }
        CustomItem ci = plugin.getItems().get(id);
        if (ci == null || ci.block() == null) {
            return;
        }
        Player p = event.getPlayer();
        if (clicked.getType().isInteractable() && !p.isSneaking()) {
            return; // let chests, doors, ... work as usual
        }
        event.setCancelled(true);
        if (p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Block target = isFree(clicked) ? clicked : clicked.getRelative(event.getBlockFace());
        if (!isFree(target)) {
            return;
        }
        if (target.getY() < target.getWorld().getMinHeight() || target.getY() >= target.getWorld().getMaxHeight()) {
            return;
        }
        for (Entity en : target.getWorld().getNearbyEntities(BoundingBox.of(target))) {
            if (en instanceof LivingEntity && !(en instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)) {
                return; // something is standing there
            }
        }

        BlockState replaced = target.getState();
        target.setType(Material.BARRIER, false);
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(target, replaced, clicked, hand, p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            replaced.update(true, false);
            return;
        }

        spawnDisplay(target, ci, p);

        if (p.getGameMode() != GameMode.CREATIVE) {
            ItemStack main = p.getInventory().getItemInMainHand();
            main.setAmount(main.getAmount() - 1);
            p.getInventory().setItemInMainHand(main.getAmount() <= 0 ? null : main);
        }
        p.swingMainHand();
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        target.getWorld().playSound(center, "block." + ci.block().sound() + ".place", 1f, 0.8f);
    }

    private static boolean isFree(Block b) {
        return b.getType().isAir() || b.isReplaceable();
    }

    private void spawnDisplay(Block target, CustomItem ci, Player placer) {
        CustomItem.BlockDef def = ci.block();
        Location loc = target.getLocation().add(0.5, 0.5, 0.5);
        loc.setYaw(snapYaw(placer.getLocation().getYaw() + 180f, def.rotationStep()));
        loc.setPitch(0f);

        ItemStack model = plugin.getItems().create(ci.id(), 1);
        ItemDisplay.ItemDisplayTransform transform;
        try {
            transform = ItemDisplay.ItemDisplayTransform.valueOf(def.transform());
        } catch (IllegalArgumentException ex) {
            transform = ItemDisplay.ItemDisplayTransform.NONE;
        }
        final ItemDisplay.ItemDisplayTransform finalTransform = transform;
        final String blockId = ci.id();

        target.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(model);
            d.setItemDisplayTransform(finalTransform);
            float s = def.scale();
            d.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
            if (def.glow()) {
                d.setBrightness(new Display.Brightness(15, 15));
            }
            d.setPersistent(true);
            d.getPersistentDataContainer().set(key, PersistentDataType.STRING, blockId);
        });
    }

    private static float snapYaw(float yaw, int step) {
        if (step <= 0) {
            return 0f;
        }
        float normalized = ((yaw % 360f) + 360f) % 360f;
        return (Math.round(normalized / step) * step) % 360f;
    }

    private void breakBlock(Player p, Block block, ItemDisplay display) {
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, p);
        internalBreak = true;
        try {
            Bukkit.getPluginManager().callEvent(breakEvent);
        } finally {
            internalBreak = false;
        }
        if (breakEvent.isCancelled()) {
            return;
        }

        String id = display.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        CustomItem ci = plugin.getItems().get(id);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        display.remove();
        block.setType(Material.AIR);

        String sound = (ci != null && ci.block() != null) ? ci.block().sound() : "stone";
        block.getWorld().playSound(center, "block." + sound + ".break", 1f, 0.8f);

        if (id != null && breakEvent.isDropItems() && p.getGameMode() != GameMode.CREATIVE) {
            ItemStack drop = plugin.getItems().create(id, 1);
            if (drop != null) {
                block.getWorld().dropItemNaturally(center, drop);
            }
        }
    }

    /** Catches breaks that did not come through a left click (e.g. creative / other plugins). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (internalBreak) {
            return;
        }
        Block b = event.getBlock();
        if (b.getType() != Material.BARRIER) {
            return;
        }
        ItemDisplay display = findDisplay(b);
        if (display == null) {
            return;
        }
        event.setCancelled(true);
        breakBlock(event.getPlayer(), b, display);
    }

    /** Self-healing: removes display entities whose barrier block was removed by other means (/fill, WorldEdit...). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof ItemDisplay d && d.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                if (d.getLocation().getBlock().getType() != Material.BARRIER) {
                    Bukkit.getScheduler().runTask(plugin, () -> d.remove());
                }
            }
        }
    }
}
