package com.example.pasturesong.qte;

import com.example.pasturesong.PastureSong;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class ChickenBrushingQTE extends QTE {
    private double progress = 0;
    private double panic = 0;
    private final double maxProgress = 100;
    private final double maxPanic = 100;
    private int tick = 0;
    private final int maxTime = 240; // 12 seconds
    
    private final LivingEntity chicken;

    public ChickenBrushingQTE(PastureSong plugin, Player player, LivingEntity chicken, Runnable onSuccess, Runnable onFailure) {
        super(plugin, player, onSuccess, onFailure);
        this.chicken = chicken;
    }

    @Override
    protected void onStart() {
        freezeEntity(chicken);
        player.sendMessage("§e[轻手轻脚] §7按右键梳理，不要惊动它！");
    }

    @Override
    protected void onTick() {
        tick++;
        if (tick > maxTime) {
            player.sendMessage("§c超时！");
            player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1f, 1f);
            fail();
            return;
        }

        // Panic decays naturally
        if (panic > 0) panic -= 2.0;
        if (panic < 0) panic = 0;
        
        // Update BossBar or ActionBar
        updateBar();
        
        // Visual Feedback for Panic Level
        if (panic > 50) {
            chicken.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, chicken.getLocation().add(0, 0.5, 0), (int)(panic / 20), 0.2, 0.2, 0.2, 0.01);
        }
    }

    @Override
    protected void onEnd() {
        unfreezeEntity(chicken);
        player.sendActionBar(Component.empty());
    }

    @Override
    public void onRightClick() {
        progress += 5.0;
        panic += 20.0; // Increased from 8.0 to make it harder to just hold right click
        
        player.playSound(player.getLocation(), Sound.ITEM_BRUSH_BRUSHING_GENERIC, 1f, 1f);
        // Visual Feedback: Brushing dust
        chicken.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, chicken.getLocation().add(0, 0.3, 0), 3, 0.2, 0.2, 0.2, 0.01);
        
        if (panic >= maxPanic) {
            player.sendMessage("§c它被吓跑了！");
            player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1f, 1f);
            chicken.getWorld().spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER, chicken.getLocation().add(0, 0.5, 0), 5);
            fail();
        } else if (progress >= maxProgress) {
            chicken.getWorld().spawnParticle(org.bukkit.Particle.HEART, chicken.getLocation().add(0, 0.5, 0), 10);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2.0f);
            success();
        } else {
            // Comfort particles if panic is low
            if (panic < 30 && progress % 10 == 0) {
                 chicken.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, chicken.getLocation().add(0, 0.5, 0), 2);
            }
            updateBar();
        }
    }
    
    private void updateBar() {
        StringBuilder bar = new StringBuilder("§e舒适度: §a");
        int pBars = (int) (progress / 5);
        for (int i=0; i<20; i++) bar.append(i < pBars ? "|" : ".");
        
        bar.append("   §c惊恐值: §c");
        int panBars = (int) (panic / 5);
        for (int i=0; i<20; i++) bar.append(i < panBars ? "|" : ".");
        
        // Add Timer
        double timeLeft = Math.max(0, (maxTime - tick) / 20.0);
        bar.append(String.format("   §b时间: %.1fs", timeLeft));
        
        player.sendActionBar(Component.text(bar.toString()));
    }
}
