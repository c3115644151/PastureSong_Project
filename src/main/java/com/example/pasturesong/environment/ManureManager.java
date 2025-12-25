package com.example.pasturesong.environment;

import com.example.pasturesong.PastureSong;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.Sound;

public class ManureManager {

    public ManureManager(PastureSong plugin) {
    }

    public void attemptDefecation(LivingEntity entity) {
        Location loc = entity.getLocation();
        // Check block under the entity
        Block block = loc.getBlock().getRelative(0, -1, 0); 
        
        // Sometimes entities sink a bit, check block at feet level if under is air? 
        // Usually entity.getLocation() is at feet. So getBlock() is the block AT feet (often air).
        // getRelative(0, -1, 0) is the block standing ON.
        
        // Logic: Only replace Grass Block or Dirt
        if (block.getType() == Material.GRASS_BLOCK || block.getType() == Material.DIRT) {
            block.setType(Material.PODZOL);
            // Squishy sound
            entity.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_BREAK, 1.0f, 0.5f); 
        }
    }
    
    public boolean cleanManure(Block block) {
        if (block.getType() == Material.PODZOL) {
            block.setType(Material.GRASS_BLOCK);
            // Drop manure item (Brown Dye as placeholder for Organic Manure)
            // In future, use custom item from CuisineFarming or PastureSong
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1, 0.5), new ItemStack(Material.BROWN_DYE)); 
            return true;
        }
        return false;
    }
}
