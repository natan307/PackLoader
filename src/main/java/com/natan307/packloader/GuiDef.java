package com.natan307.packloader;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * A custom GUI loaded from plugins/PackLoader/guis/*.yml
 *
 * @param background "namespace:path" of a PNG in the pack (textures/&lt;path&gt;.png) or null
 * @param glyph      private-use character that renders the background in the title
 */
public record GuiDef(
        String id,
        String title,
        int rows,
        String background,
        int offsetX,
        int offsetY,
        boolean hideTitle,
        char glyph,
        Map<Integer, Button> buttons) {

    public record Button(String item, Material material, String name, List<String> lore, List<String> commands) {
    }
}
