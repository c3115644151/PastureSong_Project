package com.example.pasturesong;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

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
        registerItem("GENETIC_LENS", Material.SPYGLASS, "牧原透镜", 30001, "§7透过镜片观察生命的本质。", "§e右键动物查看性状特征。");
        registerItem("DNA_SAMPLER", Material.FLINT, "DNA 采样器", 30002, "§7锋利的采样工具。", "§e右键动物提取 DNA 样本。", "§c注意：会造成动物受伤和应激。");
        // Changed to Iron Hoe to prevent vanilla shearing conflict
        registerItem("PASTURE_SHEARS", Material.IRON_HOE, "精工剪", 30003, "§7专为获取高品质羊毛设计。", "§e右键绵羊进行精准剪毛。", "§a能够完整采集特产羊毛。");
        registerItem("ANIMAL_MANURE", Material.BROWN_DYE, "动物粪便", 30004, "§7收集到的动物排泄物。", "§e可用于制作有机肥料。");
        registerItem("BUTCHER_KNIFE", Material.IRON_SWORD, "屠宰刀", 30005, "§7专为宰杀牲畜设计的刀具。", "§e自带屠宰附魔效果。", "§c能够最大化肉类产出。");
        
        plugin.getLogger().info("Registered " + customItems.size() + " PastureSong items.");
    }

    private void registerItem(String key, Material material, String name, int modelData, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Parse legacy colors (e.g. §a, §6)
            // Default name to Gold if no color specified, but we'll prepend §6
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§6" + name));
            meta.setCustomModelData(modelData);
            
            List<Component> lore = new java.util.ArrayList<>();
            for (String line : loreLines) {
                lore.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            
            // Auto-add Butchery Lore for Butcher Knife
            if (key.equals("BUTCHER_KNIFE")) {
                lore.add(Component.empty());
                lore.add(LegacyComponentSerializer.legacySection().deserialize("§7动物伤害加成: +200%")); // Visual only
                
                // Add Glow
                @SuppressWarnings("deprecation")
                Enchantment unbreaking = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("unbreaking"));
                if (unbreaking != null) {
                    meta.addEnchant(unbreaking, 1, true);
                }
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            
            meta.lore(lore);
            item.setItemMeta(meta);
            
            // Apply Enchantment using Helper
            if (key.equals("BUTCHER_KNIFE")) {
                com.example.pasturesong.enchantments.ButcheryEnchantment.apply(item, 1, plugin);
            }
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
