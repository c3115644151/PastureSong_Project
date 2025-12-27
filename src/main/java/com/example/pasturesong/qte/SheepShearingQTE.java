package com.example.pasturesong.qte;

import com.example.pasturesong.PastureSong;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class SheepShearingQTE extends QTE {
    private final Entity target;
    private double radius = 0.5;
    private boolean expanding = true;
    private final double minRadius = 0.5;
    private final double maxRadius = 2.0;
    // Increased success window: 1.2 to 1.8 (Range 0.6)
    // Speed reduced to 0.04
    // Duration: 0.6 / 0.04 = 15 ticks = 0.75 seconds (Much easier)
    private final double targetMin = 1.2;
    private final double targetMax = 1.8;
    private int successCount = 0;
    private final int requiredSuccess = 3;

    public SheepShearingQTE(PastureSong plugin, Player player, Entity target, Runnable onSuccess, Runnable onFailure) {
        super(plugin, player, onSuccess, onFailure);
        this.target = target;
    }

    @Override
    protected void onStart() {
        if (target instanceof LivingEntity) {
            freezeEntity((LivingEntity) target);
        }
        player.sendMessage("§e[精准修剪] §7请在光圈变为 §a绿色 §7时点击右键！");
    }

    @Override
    protected void onTick() {
        if (target == null || !target.isValid()) {
            fail();
            return;
        }

        // Pulse logic
        // Slower speed for better reaction time
        double speed = 0.04 + (successCount * 0.01); 
        if (expanding) {
            radius += speed;
            if (radius >= maxRadius) expanding = false;
        } else {
            radius -= speed;
            if (radius <= minRadius) expanding = true;
        }
        
        // Check if in target zone
        boolean isGreen = radius >= targetMin && radius <= targetMax;
        
        Location center = target.getLocation().add(0, 0.5, 0);
        
        // Determine Color
        org.bukkit.Particle.DustOptions dustOptions;
        if (isGreen) {
            dustOptions = new org.bukkit.Particle.DustOptions(org.bukkit.Color.LIME, 1.2f);
        } else {
            dustOptions = new org.bukkit.Particle.DustOptions(org.bukkit.Color.WHITE, 0.8f);
        }

        // Render Single Ring
        for (int i = 0; i < 24; i++) {
            double angle = 2 * Math.PI * i / 24;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            target.getWorld().spawnParticle(org.bukkit.Particle.DUST, center.clone().add(x, 0, z), 1, 0, 0, 0, 0, dustOptions);
        }
        
        // Visual feedback for target count
        player.sendActionBar(Component.text("§e进度: " + successCount + "/" + requiredSuccess));
    }

    @Override
    protected void onEnd() {
        player.sendActionBar(Component.empty());
    }

    @Override
    public void onRightClick() {
        if (radius >= targetMin && radius <= targetMax) {
            successCount++;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f + (successCount * 0.2f));
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1, 0), 3);
            
            if (successCount >= requiredSuccess) {
                // Final Success: Wool explosion
                target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                player.playSound(player.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
                success();
            } else {
                // Reset radius for next round slightly
                radius = minRadius;
                expanding = true;
            }
        } else {
            player.sendMessage("§c时机不对！");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            target.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, target.getLocation().add(0, 1, 0), 5);
            fail();
        }
    }
}
