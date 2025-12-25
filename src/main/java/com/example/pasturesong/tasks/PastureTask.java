package com.example.pasturesong.tasks;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.environment.EnvironmentManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * PastureTask
 * Periodic task that handles:
 * 1. Stress Decay (gradually reduce temporary stress)
 * 2. Defecation (Manure generation)
 * 3. Visual Effects (Stress particles)
 */
public class PastureTask extends BukkitRunnable {

    private final PastureSong plugin;

    public PastureTask(PastureSong plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Clear Environment Cache at start of cycle
        plugin.getEnvironmentManager().clearCache();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(Animals.class)) {
                if (!(entity instanceof Animals)) continue;
                Animals animal = (Animals) entity;
                
                // 1. Stress Decay
                // Runs every 5 seconds (100 ticks)
                // Decay 1 stress per 5 seconds (Updated)
                plugin.getStressManager().decayStress(animal, 1.0);
                
                // 2. Visual Effects for High Stress
                double totalStress = plugin.getStressManager().getTotalStress(animal);
                if (totalStress > 50.0) {
                    // Show Angry Villager particles
                    world.spawnParticle(Particle.ANGRY_VILLAGER, animal.getLocation().add(0, 1, 0), 1);
                }
                
                // 2.1 Visual Effects & Stress for Exhaustion
                if (plugin.getExhaustionManager().isExhausted(animal)) {
                    plugin.getExhaustionManager().playExhaustionEffect(animal);
                }
                
                // 2.2 Disease Update
        plugin.getDiseaseManager().update(animal);
        
        // 2.3 Malignant Mutation Check (Environment/Stress)
        // Check every tick? No, that's too frequent. This task runs every 5s.
        // Probability: If stressed > 70 or overloaded, chance to degrade gene.
        double stress = plugin.getStressManager().getTotalStress(animal);
        boolean overloaded = plugin.getEnvironmentManager().getLoad(animal).overloaded;
        
        if (stress > 70.0 || overloaded) {
            // 5% chance per 5s to trigger mutation check
            if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.05) {
                // If check triggers, attempt mutation with 50% success rate (so 2.5% total per 5s if condition met)
                // Actually let's use the method's chance parameter.
                plugin.getGeneticsManager().attemptExistingMutation(animal, 0.5, true); 
                // "true" means malignant (only degrade)
            }
        }
                
                // 3. Defecation Logic
                // Chance: 5% every 5 seconds? That's quite frequent.
                // 5% = 1/20. So once every 100 seconds on average.
                if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                    plugin.getManureManager().attemptDefecation(animal);
                }
                
                // 5. Overload & Land Degradation
                EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(animal);
                if (load.overloaded) {
                    // Chance to degrade land: (Ratio - 1.0)^2 * 0.1
                    // Non-linear increase in degradation chance as requested
                    double ratioDelta = load.overloadRatio - 1.0;
                    double chance = ratioDelta * ratioDelta * 0.1;

                    // Cap chance at 100%
                    if (chance > 1.0) chance = 1.0;

                    if (ThreadLocalRandom.current().nextDouble() < chance) {
                        Block floor = animal.getLocation().getBlock().getRelative(0, -1, 0);
                        if (floor.getType() == Material.GRASS_BLOCK) {
                            floor.setType(Material.DIRT);
                            world.playSound(floor.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.5f, 0.8f);
                            world.spawnParticle(Particle.BLOCK, floor.getLocation().add(0.5, 1, 0.5), 5, 0.2, 0.1, 0.2, floor.getBlockData());
                        }
                    }
                }

                // 4. Weather Stress (Rain/Thunder + Exposed)
                if (world.hasStorm()) {
                    // Check if exposed to sky
                    // getHighestBlockYAt returns Y of highest non-air block
                    int highestY = world.getHighestBlockYAt(animal.getLocation());
                    if (highestY <= animal.getLocation().getBlockY() + 1) {
                        // Exposed to rain!
                        // Thunder adds more stress (Reduced to 40% of original: 2.0 / 4.0)
                        double stressAdd = world.isThundering() ? 4.0 : 2.0;
                        plugin.getStressManager().addTemporaryStress(animal, stressAdd);
                    }
                }
            }
        }
    }
}
