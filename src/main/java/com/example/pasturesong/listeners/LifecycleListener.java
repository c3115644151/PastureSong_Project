package com.example.pasturesong.listeners;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.genetics.GeneData;
import com.example.pasturesong.genetics.GenePair;
import com.example.pasturesong.genetics.Trait;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;

public class LifecycleListener implements Listener {

    private final PastureSong plugin;

    public LifecycleListener(PastureSong plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Animals)) return;
        Animals animal = (Animals) event.getEntity();

        // Only handle natural spawns or egg spawns for initial gene generation
        // Breeding events are handled separately
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL || 
            event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG ||
            event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.DEFAULT) {
            
            // Check if already has genes (might be chunk load or persistent entity)
            // If we assume a fresh entity has no genes, we can check a flag.
            
            if (!animal.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "gene_initialized"), org.bukkit.persistence.PersistentDataType.BYTE)) {
                GeneData randomGenes = plugin.getGeneticsManager().generateRandomGenes();
                plugin.getGeneticsManager().saveGenesToEntity(animal, randomGenes);
                markInitialized(animal);
                
                // If it's a baby (rare for natural spawn, but possible), apply growth logic?
                if (!animal.isAdult()) {
                    applyGrowthLogic(animal, randomGenes);
                }
            }
        }
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getEntity() instanceof Animals)) return;
        Animals child = (Animals) event.getEntity();
        LivingEntity father = event.getFather();
        LivingEntity mother = event.getMother();

        // Breeding Constraints: Stress & Disease
        if (father instanceof Animals && mother instanceof Animals) {
            Animals dad = (Animals) father;
            Animals mom = (Animals) mother;
            
            boolean dadBad = plugin.getStressManager().getTotalStress(dad) > 70 || plugin.getDiseaseManager().isSick(dad);
            boolean momBad = plugin.getStressManager().getTotalStress(mom) > 70 || plugin.getDiseaseManager().isSick(mom);
            
            if (dadBad || momBad) {
                event.setCancelled(true);
                // Feedback
                if (dadBad) dad.getWorld().spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER, dad.getLocation().add(0, 1, 0), 5);
                if (momBad) mom.getWorld().spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER, mom.getLocation().add(0, 1, 0), 5);
                return;
            }
        }

        GeneData fatherGenes = plugin.getGeneticsManager().getGenesFromEntity(father);
        GeneData motherGenes = plugin.getGeneticsManager().getGenesFromEntity(mother);

        GeneData childGenes = plugin.getGeneticsManager().mixGenes(fatherGenes, motherGenes);
        plugin.getGeneticsManager().saveGenesToEntity(child, childGenes);
        markInitialized(child);

        // Apply Growth Logic (V gene)
        applyGrowthLogic(child, childGenes);
    }

    private void markInitialized(LivingEntity entity) {
        entity.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "gene_initialized"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
    }

    private void applyGrowthLogic(Animals animal, GeneData genes) {
        if (animal.isAdult()) return; // Should not happen for babies
        
        GenePair vPair = genes.getGenePair(Trait.VITALITY);
        double vVal = vPair.getPhenotypeValue(); 
        // vVal ranges from roughly -5 to +5 (depending on Allele values).
        // Default age is -24000 (20 mins).
        // Higher V -> Faster growth -> Smaller negative number (closer to 0).
        // Let's say each point of V reduces time by 10%.
        
        int baseTicks = -24000;
        double multiplier = 1.0;
        
        // If V is positive (Strong), reduce time.
        // If V is negative (Weak), increase time?
        // Let's simply say: Time = Base * (1.0 - (V * 0.05))
        // V=5 -> 0.75 * 24000 = 18000 ticks (15 mins)
        // V=-5 -> 1.25 * 24000 = 30000 ticks (25 mins)
        
        multiplier -= (vVal * 0.05);
        
        int newAge = (int) (baseTicks * multiplier);
        animal.setAge(newAge);
    }
}
