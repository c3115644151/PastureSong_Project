package com.example.pasturesong.production;

import com.example.pasturesong.PastureSong;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ExhaustionManager {
    private final NamespacedKey EXHAUSTION_KEY;

    public ExhaustionManager(PastureSong plugin) {
        this.EXHAUSTION_KEY = new NamespacedKey(plugin, "exhaustion_timestamp");
    }

    /**
     * Set the entity as exhausted for a duration (in milliseconds).
     */
    public void setExhausted(LivingEntity entity, long durationMillis) {
        long expiry = System.currentTimeMillis() + durationMillis;
        entity.getPersistentDataContainer().set(EXHAUSTION_KEY, PersistentDataType.LONG, expiry);
    }

    /**
     * Check if the entity is currently exhausted.
     */
    public boolean isExhausted(LivingEntity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(EXHAUSTION_KEY, PersistentDataType.LONG)) return false;
        
        long expiry = pdc.get(EXHAUSTION_KEY, PersistentDataType.LONG);
        return System.currentTimeMillis() < expiry;
    }
    
    /**
     * Get remaining exhaustion time in milliseconds. Returns 0 if not exhausted.
     */
    public long getRemainingTime(LivingEntity entity) {
        if (!isExhausted(entity)) return 0;
        long expiry = entity.getPersistentDataContainer().get(EXHAUSTION_KEY, PersistentDataType.LONG);
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    /**
     * Clear exhaustion.
     */
    public void clearExhaustion(LivingEntity entity) {
        entity.getPersistentDataContainer().remove(EXHAUSTION_KEY);
    }
    
    /**
     * Spawn visual effects for exhausted entity.
     */
    public void playExhaustionEffect(LivingEntity entity) {
        if (isExhausted(entity)) {
            // Smoke particles (Gray smoke as requested)
            entity.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, entity.getLocation().add(0, entity.getHeight() + 0.5, 0), 3, 0.1, 0.1, 0.1, 0.01);
            
            // Subtle breathing/tired sound (Low chance to avoid spam in loop)
            if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.2) {
                 entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_LLAMA_SPIT, 0.5f, 0.5f); // Sigh-like sound
            }
        }
    }
}
