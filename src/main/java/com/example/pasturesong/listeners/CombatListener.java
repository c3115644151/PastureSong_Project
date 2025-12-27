package com.example.pasturesong.listeners;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.enchantments.ButcheryEnchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class CombatListener implements Listener {

    private final PastureSong plugin;

    public CombatListener(PastureSong plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player player = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if using Butcher Knife
        if (plugin.getItemManager().isCustomItem(item, "BUTCHER_KNIFE")) {
            // Base Damage: 7
            double damage = 7.0;

            // Check if target is a valid livestock animal
            if (ToolListener.isLivestock(target)) {
                // +200% Bonus (Total 21)
                damage += 14.0;

                // Check Butchery Enchantment
                int level = ButcheryEnchantment.getLevel(item, plugin);
                if (level > 0) {
                    // +10 per level
                    damage += (level * 10.0);
                }
            }

            event.setDamage(damage);
        }
    }
}
