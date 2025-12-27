package com.example.pasturesong.disease;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.environment.EnvironmentManager;
import com.example.pasturesong.genetics.GeneData;
import com.example.pasturesong.genetics.Trait;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DiseaseManager {

    private final PastureSong plugin;
    private final NamespacedKey sickKey;

    public DiseaseManager(PastureSong plugin) {
        this.plugin = plugin;
        this.sickKey = new NamespacedKey(plugin, "is_sick");
    }

    public void update(Animals animal) {
        if (isSick(animal)) {
            handleSickTick(animal);
        } else {
            handleInfectionCheck(animal);
        }
    }

    public boolean isSick(Animals animal) {
        return animal.getPersistentDataContainer().has(sickKey, PersistentDataType.BYTE);
    }

    public void setSick(Animals animal, boolean sick) {
        if (sick) {
            animal.getPersistentDataContainer().set(sickKey, PersistentDataType.BYTE, (byte) 1);
            // Initial effect: Infection sound and Sneeze particles
            animal.getWorld().spawnParticle(Particle.SNEEZE, animal.getLocation().add(0, animal.getHeight(), 0), 10, 0.2, 0.2, 0.2, 0.1);
            animal.getWorld().playSound(animal.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 0.5f, 1.5f);
        } else {
            animal.getPersistentDataContainer().remove(sickKey);
            // Cure effect: Levelup sound and Heart particles
            animal.getWorld().spawnParticle(Particle.HEART, animal.getLocation().add(0, animal.getHeight(), 0), 5, 0.3, 0.3, 0.3, 0.1);
            animal.getWorld().playSound(animal.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
    }

    private void handleSickTick(Animals animal) {
        // Visuals
        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            animal.getWorld().spawnParticle(Particle.SNEEZE, animal.getLocation().add(0, animal.getHeight() * 0.8, 0), 3, 0.1, 0.1, 0.1, 0.05);
        }

        // Add Stress (Disease causes discomfort)
        // Removed as per request (Flu only causes buff/damage, not stress)
        // plugin.getStressManager().addTemporaryStress(animal, 2.0);
        
        // Influenza Buff - Periodic Damage
        // Slow damage: e.g. 0.5 damage every 10 seconds. This tick runs every 5s.
        // So 50% chance to damage 0.5 (or just 1 damage every 20s?)
        // User said: "slow damage... until death"
        if (ThreadLocalRandom.current().nextDouble() < 0.1) { // 10% chance per 5s = avg once per 50s
            animal.damage(1.0);
        }

        // Transmission Logic
        // 5s interval. 1% chance to transmit.
        if (ThreadLocalRandom.current().nextDouble() < 0.01) {
            List<Entity> nearby = animal.getNearbyEntities(5, 5, 5);
            boolean transmitted = false;
            
            // 1. Try same species
            for (Entity e : nearby) {
                if (e.getClass() == animal.getClass() && !isSick((Animals) e)) {
                    if (checkInfection((Animals) e, 1.0)) { // Base chance is 1.0 here because we already rolled the 1%
                        setSick((Animals) e, true);
                        transmitted = true;
                        break; // Transmit to one target? "cancel this transmission" implies single target logic.
                    }
                }
            }
            
            // 2. If not transmitted, try other animals
            if (!transmitted) {
                for (Entity e : nearby) {
                    if (e instanceof Animals && !isSick((Animals) e)) {
                         if (checkInfection((Animals) e, 1.0)) {
                            setSick((Animals) e, true);
                            transmitted = true;
                            break;
                        }
                    }
                }
            }
            
            // 3. If not transmitted, try Player
            if (!transmitted) {
                 for (Entity e : nearby) {
                    if (e instanceof org.bukkit.entity.Player) {
                        org.bukkit.entity.Player p = (org.bukkit.entity.Player) e;
                        // Player gets "Influenza" buff - Weakness and Slowness
                        p.sendActionBar(net.kyori.adventure.text.Component.text("§c☣ 警告：你接触了感染源并感染了流感！ ☣"));
                        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 0.5f, 0.5f);
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 600, 0));
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 600, 0));
                        transmitted = true;
                        break;
                    }
                 }
            }
        }
        
        // Natural Recovery Check
        // Needs low stress and good health
        if (plugin.getStressManager().getTotalStress(animal) < 30.0 && animal.getHealth() > animal.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() * 0.8) {
             GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(animal);
             double resistance = 0;
             if (genes != null) {
                 resistance = genes.getGenePair(Trait.RESISTANCE).getPhenotypeValue();
             }
             
             // Base recovery chance 5% + Resistance * 1%
             double recoveryChance = 0.05 + (resistance * 0.01);
             if (ThreadLocalRandom.current().nextDouble() < recoveryChance) {
                 setSick(animal, false);
             }
        }
    }

    private void handleInfectionCheck(Animals animal) {
        // Base chance is 0, relies on triggers
        double infectionRisk = 0.0;

        // 1. Manure / Dirty Environment
        EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(animal);
        if (load.overloaded) {
            // Overload increases risk significantly
            infectionRisk += (load.overloadRatio - 1.0) * 0.1; // e.g. 1.5 ratio -> +5% risk
        }

        // 2. High Stress
        double stress = plugin.getStressManager().getTotalStress(animal);
        if (stress > 70.0) {
            infectionRisk += 0.02; // +2% risk if very stressed (Reduced from 5%)
        }

        // 3. Genetics (Resistance)
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(animal);
        if (genes != null) {
            double resistance = genes.getGenePair(Trait.RESISTANCE).getPhenotypeValue();
            // Resistance reduces risk. Range approx -14 to +14
            // -10 Res -> +10% risk. +10 Res -> -10% risk.
            infectionRisk -= (resistance * 0.01);
        }

        // Cap risk
        if (infectionRisk < 0) infectionRisk = 0;
        if (infectionRisk > 1.0) infectionRisk = 1.0;

        // Roll dice (This runs every 5s)
        // Multiplier lowered to 0.005 as requested (0.5% max chance base).
        
        if (infectionRisk > 0 && ThreadLocalRandom.current().nextDouble() < (infectionRisk * 0.005)) {
            setSick(animal, true);
        }
    }

    /**
     * Check if infection succeeds based on resistance gene.
     * @param animal The animal to check
     * @param baseChance Base probability (0.0 - 1.0)
     * @return true if infected
     */
    public boolean checkInfection(Animals animal, double baseChance) {
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(animal);
        double resistance = 0;
        if (genes != null) {
            resistance = genes.getGenePair(Trait.RESISTANCE).getPhenotypeValue();
        }
        
        // Resistance lowers chance.
        // Formula: FinalChance = BaseChance * (1.0 - (Resistance * 0.1))
        // R=5 -> 0.5x chance. R=-5 -> 1.5x chance.
        double chance = baseChance * (1.0 - (resistance * 0.1));
        
        // Clamp chance
        if (chance < 0.0) chance = 0.0;
        if (chance > 1.0) chance = 1.0;
        
        return ThreadLocalRandom.current().nextDouble() < chance;
    }
}
