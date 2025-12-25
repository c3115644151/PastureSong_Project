package com.example.pasturesong.stress;

import com.example.pasturesong.PastureSong;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class StressListener implements Listener {
    private final PastureSong plugin;

    public StressListener(PastureSong plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Animals)) return;
        Animals animal = (Animals) event.getEntity();
        
        double damage = event.getFinalDamage();
        // 1 damage = 10 stress
        plugin.getStressManager().addTemporaryStress(animal, damage * 10.0);
    }
    
    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        // Find nearby animals and add stress
        double radius = 10.0;
        for (Entity entity : event.getLocation().getWorld().getNearbyEntities(event.getLocation(), radius, radius, radius)) {
            if (entity instanceof Animals) {
                double dist = entity.getLocation().distance(event.getLocation());
                // Closer = more stress. Max 100 stress at center.
                double stress = 100.0 * (1.0 - (dist / radius));
                if (stress > 0) {
                    plugin.getStressManager().addTemporaryStress((Animals) entity, stress);
                }
            }
        }
    }
}
