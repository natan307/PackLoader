package com.natan307.packloader;

import org.bukkit.Material;

import java.util.List;

/**
 * One item loaded from plugins/PackLoader/items/*.yml
 *
 * @param itemModel resolved "namespace:id" for the item_model component (may be null)
 * @param generate  true when PackLoader must generate the item definition (model/texture given)
 * @param block     non-null when the item can be placed as a custom 3D block
 */
public record CustomItem(
        String id,
        String displayName,
        List<String> lore,
        Material material,
        String itemModel,
        boolean generate,
        String model,
        String texture,
        String parent,
        Integer customModelData,
        boolean glint,
        boolean unbreakable,
        boolean hideFlags,
        BlockDef block) {

    /**
     * @param scale        size multiplier of the displayed model (1.0 = one block)
     * @param rotationStep snap the facing to multiples of this many degrees (0 = never rotate)
     * @param transform    ItemDisplay transform (NONE, FIXED, HEAD, GROUND, GUI ...)
     * @param glow         render at full brightness
     * @param sound        sound family: plays block.&lt;sound&gt;.place / block.&lt;sound&gt;.break
     */
    public record BlockDef(float scale, int rotationStep, String transform, boolean glow, String sound) {
    }
}
