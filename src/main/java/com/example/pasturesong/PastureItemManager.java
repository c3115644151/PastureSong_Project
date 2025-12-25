package com.example.pasturesong;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PastureItemManager {
    private final PastureSong plugin;
    private final Map<String, ItemStack> customItems = new HashMap<>();

    public PastureItemManager(PastureSong plugin) {
        this.plugin = plugin;
        registerItems();
    }

    private void registerItems() {
        plugin.getLogger().info("Registering PastureSong items...");

        // Tools
        registerItem("GENETIC_LENS", Material.SPYGLASS, "基因透镜", 30001, "§7透过镜片观察生命的本质。", "§e右键动物查看基因性状。");
        registerItem("DNA_SAMPLER", Material.FLINT, "DNA 采样器", 30002, "§7锋利的采样工具。", "§e右键动物提取 DNA 样本。", "§c注意：会造成动物受伤和应激。");
        
        plugin.getLogger().info("Registered " + customItems.size() + " PastureSong items.");
    }

    private void registerItem(String key, Material material, String name, int modelData, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.GOLD));
            meta.setCustomModelData(modelData);
            
            List<Component> lore = new java.util.ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line));
            }
            meta.lore(lore);
            
            item.setItemMeta(meta);
        }
        customItems.put(key, item);
    }

    public ItemStack getItem(String key) {
        return customItems.get(key) != null ? customItems.get(key).clone() : null;
    }
    
    public boolean isCustomItem(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemStack target = customItems.get(key);
        if (target == null) return false;
        
        if (item.getType() != target.getType()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        
        return item.getItemMeta().getCustomModelData() == target.getItemMeta().getCustomModelData();
    }
}
