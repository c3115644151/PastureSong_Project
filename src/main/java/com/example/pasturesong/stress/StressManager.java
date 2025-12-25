package com.example.pasturesong.stress;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.environment.EnvironmentManager;
import com.example.pasturesong.genetics.GeneData;
import com.example.pasturesong.genetics.GenePair;
import com.example.pasturesong.genetics.Trait;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class StressManager {
    private final PastureSong plugin;
    private final NamespacedKey TEMP_STRESS_KEY;
    private double exhaustionPenalty;

    public StressManager(PastureSong plugin) {
        this.plugin = plugin;
        this.TEMP_STRESS_KEY = new NamespacedKey(plugin, "temp_stress");
        loadConfig();
    }
    
    public void loadConfig() {
        this.exhaustionPenalty = plugin.getConfig().getDouble("stress.exhaustion_penalty", 10.0);
    }

    public void addTemporaryStress(LivingEntity entity, double amount) {
        double current = getTemporaryStress(entity);
        setTemporaryStress(entity, current + amount);
    }

    public double getTemporaryStress(LivingEntity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.getOrDefault(TEMP_STRESS_KEY, PersistentDataType.DOUBLE, 0.0);
    }

    public void setTemporaryStress(LivingEntity entity, double amount) {
        if (amount < 0) amount = 0;
        entity.getPersistentDataContainer().set(TEMP_STRESS_KEY, PersistentDataType.DOUBLE, amount);
    }

    public double calculateBaseStress(LivingEntity entity) {
        double base = 50.0; // Default base stress
        
        // 1. Genetics (Resistance)
        // Higher Resistance -> Lower Base Stress
        // R value ranges from -6 to +6 (approx).
        // Let's say R=0 is neutral. R=+6 reduces stress by 30. R=-6 adds 30.
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(entity);
        GenePair rPair = genes.getGenePair(Trait.RESISTANCE);
        double rVal = rPair.getPhenotypeValue(); 
        
        // R > 0 reduces stress, R < 0 increases stress
        base -= (rVal * 5.0); 

        // 2. Health
        double maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        double healthPct = entity.getHealth() / maxHealth;
        
        if (healthPct < 0.8) {
            // Health loss adds stress. 50% health -> +25 stress. 10% health -> +45 stress.
            base += (0.8 - healthPct) * 50.0; 
        }

        // 3. Environment (Placeholder)
        // TODO: Add Manure / Load factor

        // 4. Exhaustion (New)
        // Constant stress if exhausted
        if (plugin.getExhaustionManager().isExhausted(entity)) {
            base += exhaustionPenalty;
        }
        
        // 5. Overload (New)
        // Exponential penalty if load > capacity
        EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(entity);
        if (load.overloaded) {
            // Formula: 50 * (ratio - 1)^2
            // Ratio 1.2 (+20%) -> 2 stress
            // Ratio 1.5 (+50%) -> 12.5 stress
            // Ratio 2.0 (+100%) -> 50 stress
            double penalty = 50.0 * Math.pow(load.overloadRatio - 1.0, 2);
            base += penalty;
        }

        return Math.max(0, base);
    }
    
    public double getTotalStress(LivingEntity entity) {
        return calculateBaseStress(entity) + getTemporaryStress(entity);
    }
    
    /**
     * Decay temporary stress over time.
     * Should be called periodically.
     */
    public void decayStress(LivingEntity entity, double amount) {
        double current = getTemporaryStress(entity);
        if (current > 0) {
            setTemporaryStress(entity, current - amount);
        }
    }
}
