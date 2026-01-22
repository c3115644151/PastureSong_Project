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

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import java.io.File;
import org.bukkit.persistence.PersistentDataType;
import com.nexuscore.util.NexusKeys;

public class PastureItemManager {
    private final PastureSong plugin;
    private final Map<String, ItemStack> customItems = new HashMap<>();

    public PastureItemManager(PastureSong plugin) {
        this.plugin = plugin;
        loadItems();
    }

    private void loadItems() {
        plugin.getLogger().info("Loading PastureSong items...");
        customItems.clear();

        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) {
            plugin.saveResource("items.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection itemsSection = config.getConfigurationSection("items");

        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                try {
                    registerConfigItem(key, itemsSection.getConfigurationSection(key));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load item: " + key);
                    e.printStackTrace();
                }
            }
        }
        
        plugin.getLogger().info("Registered " + customItems.size() + " PastureSong items.");
    }

    private void registerConfigItem(String key, ConfigurationSection section) {
        String matName = section.getString("material");
        Material material = Material.matchMaterial(matName);
        if (material == null) {
            plugin.getLogger().warning("Invalid material for " + key + ": " + matName);
            return;
        }

        String name = section.getString("name");
        int modelData = section.getInt("custom-model-data");
        List<String> lore = section.getStringList("lore");
        boolean hasStar = section.getBoolean("star", false);

        registerItem(key, material, name, modelData, hasStar, lore.toArray(new String[0]));
    }

    private void registerItem(String key, Material material, String name, int modelData, boolean hasStar, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Parse legacy colors (e.g. §a, §6)
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
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

            // Set NexusCore Unified ID
            meta.getPersistentDataContainer().set(NexusKeys.ITEM_ID, PersistentDataType.STRING, key);

            // Set Star Flag
            org.bukkit.NamespacedKey starKey = new org.bukkit.NamespacedKey(plugin, "nexus_has_star");
            meta.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, hasStar ? 1 : 0);

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
        if (item == null || !item.hasItemMeta())
            return false;
        ItemStack target = customItems.get(key);
        if (target == null)
            return false;

        if (item.getType() != target.getType())
            return false;
        if (!item.getItemMeta().hasCustomModelData())
            return false;

        return item.getItemMeta().getCustomModelData() == target.getItemMeta().getCustomModelData();
    }

    public java.util.Collection<ItemStack> getAllItems() {
        return customItems.values();
    }
}
