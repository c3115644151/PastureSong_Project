package com.example.pasturesong.environment;

import com.example.pasturesong.PastureSong;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class EnvironmentListener implements Listener {

    private final PastureSong plugin;

    public EnvironmentListener(PastureSong plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        plugin.getEnvironmentManager().handleBlockChange(block, block.getType(), Material.AIR);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material newType = event.getBlockPlaced().getType();
        Material oldType = event.getBlockReplacedState().getType();
        
        plugin.getEnvironmentManager().handleBlockChange(block, oldType, newType);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // Sheep eating grass, Enderman, etc.
        Block block = event.getBlock();
        Material oldType = block.getType();
        Material newType = event.getTo();
        
        plugin.getEnvironmentManager().handleBlockChange(block, oldType, newType);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        Material type = block.getType();
        // Check for Fence Gates, Doors, Trapdoors
        if (Tag.FENCE_GATES.isTagged(type) || Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type)) {
            // The interaction might have changed the state (Open/Close).
            // We assume the state change happened (MONITOR).
            // We trigger a block change notification to invalidate cache.
            plugin.getEnvironmentManager().handleBlockChange(block, type, type);
        }
    }
}
