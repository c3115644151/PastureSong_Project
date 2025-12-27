package com.example.pasturesong.environment;

import com.example.pasturesong.PastureSong;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.Sound;

public class ManureManager {

    private final PastureSong plugin;

    public ManureManager(PastureSong plugin) {
        this.plugin = plugin;
    }

    public void attemptDefecation(LivingEntity entity) {
        // Only valid pasture animals excrete
        if (!plugin.getEnvironmentManager().getLoad(entity).isValid) {
            return;
        }

        Location loc = entity.getLocation();
        // Check block under the entity
        Block block = loc.getBlock().getRelative(0, -1, 0); 
        
        // Sometimes entities sink a bit, check block at feet level if under is air? 
        // Usually entity.getLocation() is at feet. So getBlock() is the block AT feet (often air).
        // getRelative(0, -1, 0) is the block standing ON.
        
        // Logic: Only replace Grass Block or Dirt
        Material oldType = block.getType();
        if (oldType == Material.GRASS_BLOCK || oldType == Material.DIRT) {
            block.setType(Material.PODZOL);
            // Notify EnvironmentManager
            plugin.getEnvironmentManager().handleBlockChange(block, oldType, Material.PODZOL);
            
            // Squishy sound
            entity.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_BREAK, 1.0f, 0.5f);
            // Smell particles (Cosy Smoke to simulate stench)
            entity.getWorld().spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, loc.add(0, 0.5, 0), 5, 0.2, 0.1, 0.2, 0.01);
        }
    }
    
    public boolean cleanManure(Block block) {
        if (block.getType() == Material.PODZOL) {
            block.setType(Material.GRASS_BLOCK);
            // Notify EnvironmentManager
            plugin.getEnvironmentManager().handleBlockChange(block, Material.PODZOL, Material.GRASS_BLOCK);
            
            // Drop manure item (ANIMAL_MANURE)
            ItemStack manure = plugin.getItemManager().getItem("ANIMAL_MANURE");
            if (manure == null) {
                manure = new ItemStack(Material.BROWN_DYE); // Fallback
            }
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1, 0.5), manure); 
            
            // Feedback: Clean effect
            block.getWorld().playSound(block.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
            block.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 1.2, 0.5), 5, 0.3, 0.3, 0.3);
            return true;
        }
        return false;
    }
}
