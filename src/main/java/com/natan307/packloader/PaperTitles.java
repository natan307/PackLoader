package com.natan307.packloader;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Isolated in its own class so servers without Adventure (plain Spigot) never load it.
 * Inventory titles with a custom font need Adventure components.
 */
final class PaperTitles {

    private PaperTitles() {
    }

    static Inventory create(InventoryHolder holder, int size, String namespace, String glyphs, String legacyText) {
        Component title = Component.empty();
        if (!glyphs.isEmpty()) {
            // white = no tint, so the background texture keeps its original colours
            title = title.append(Component.text(glyphs, NamedTextColor.WHITE).font(Key.key(namespace, "gui")));
        }
        if (!legacyText.isEmpty()) {
            title = title.append(LegacyComponentSerializer.legacySection().deserialize(legacyText));
        }
        return Bukkit.createInventory(holder, size, title);
    }
}
