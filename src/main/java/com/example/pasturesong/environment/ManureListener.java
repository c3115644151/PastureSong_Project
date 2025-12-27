package com.example.pasturesong.environment;

import com.example.pasturesong.PastureSong;
import org.bukkit.Material;
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
                // Prevent vanilla path creation
                event.setCancelled(true);
                
                boolean success = plugin.getManureManager().cleanManure(event.getClickedBlock());
                if (success) {
                    // Manually damage the item since event is cancelled
                    item.damage(1, event.getPlayer());
                }
            }
        }
    }
}
