package com.example.pasturesong.enchantments;

import com.example.pasturesong.PastureSong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ButcheryEnchantment {
    
    private static final String KEY_NAME = "enchantment_butchery";
    
    public static void apply(ItemStack item, int level, PastureSong plugin) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        NamespacedKey key = new NamespacedKey(plugin, KEY_NAME);
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
        
        // Update Lore
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        
        // Remove existing Butchery lore if present
        lore.removeIf(c -> PlainTextComponentSerializer.plainText().serialize(c).contains("屠宰"));
        
        // Add new Lore
        String rom = toRoman(level);
        lore.add(Component.text("§7屠宰 " + rom));
        
        meta.lore(lore);
        item.setItemMeta(meta);
    }
    
    public static int getLevel(ItemStack item, PastureSong plugin) {
        if (item == null || !item.hasItemMeta()) return 0;
        NamespacedKey key = new NamespacedKey(plugin, KEY_NAME);
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
    }
    
    private static String toRoman(int n) {
        if (n <= 0) return "";
        if (n == 1) return "I";
        if (n == 2) return "II";
        if (n == 3) return "III";
        if (n == 4) return "IV";
        if (n == 5) return "V";
        return String.valueOf(n);
    }
}
