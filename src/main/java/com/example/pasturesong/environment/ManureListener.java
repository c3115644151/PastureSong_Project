package com.example.pasturesong.environment;

import com.example.pasturesong.PastureSong;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ManureListener implements Listener {
    private final PastureSong plugin;

    public ManureListener(PastureSong plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onManureClean(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        
        ItemStack item = event.getItem();
        if (item == null) return;
        
        // Check for Shovel
        if (item.getType().name().endsWith("_SHOVEL")) {
            if (event.getClickedBlock().getType() == Material.PODZOL) {
                boolean success = plugin.getManureManager().cleanManure(event.getClickedBlock());
                if (success) {
                    event.getPlayer().playSound(event.getClickedBlock().getLocation(), Sound.ITEM_HOE_TILL, 1.0f, 1.0f);
                    // Damage shovel?
                    // item.damage(1, event.getPlayer());
                }
            }
        }
    }
}
