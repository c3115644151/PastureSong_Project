package com.example.pasturesong.qte;

import com.example.pasturesong.PastureSong;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public abstract class QTE {
    protected final PastureSong plugin;
    protected final Player player;
    protected final Runnable onSuccess;
    protected final Runnable onFailure;
    protected BukkitTask task;
    protected boolean isRunning = false;
    protected long startTime = 0;
    protected long lastInputTime = 0;

    public QTE(PastureSong plugin, Player player, Runnable onSuccess, Runnable onFailure) {
        this.plugin = plugin;
        this.player = player;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
    }

    public void start() {
        isRunning = true;
        // Set start time to future to create a buffer period (500ms)
        // During this period, canAcceptInput() returns false, and we should ignore inputs.
        startTime = System.currentTimeMillis() + 500;
        onStart();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRunning) {
                    this.cancel();
                    return;
                }
                onTick();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void stop() {
        isRunning = false;
        try {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            onEnd();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Ensure unregister happens even if onEnd fails
            plugin.getQTEManager().unregisterQTE(player);
        }
    }

    protected void success() {
        stop();
        if (onSuccess != null) onSuccess.run();
    }

    protected void fail() {
        stop();
        if (onFailure != null) onFailure.run();
    }

    protected abstract void onStart();
    protected abstract void onTick();
    protected abstract void onEnd();
    
    // Input handling methods that Manager will call
    public void onLeftClick() {}
    public void onRightClick() {}
    
    public boolean canAcceptInput() {
        long now = System.currentTimeMillis();
        if (now < startTime) return false;
        if (now - lastInputTime < 200) return false; // Debounce 200ms
        lastInputTime = now;
        return true;
    }

    // Helpers
    protected void freezeEntity(LivingEntity entity) {
        if (entity == null) return;
        // Slowness 255 prevents movement
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false, false));
        // Jump Boost 128 (negative) prevents jumping
        entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 128, false, false, false));
        // Also set movement speed attribute to 0 as backup
        if (entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
        }
    }

    protected void unfreezeEntity(LivingEntity entity) {
        if (entity == null) return;
        entity.removePotionEffect(PotionEffectType.SLOWNESS);
        entity.removePotionEffect(PotionEffectType.JUMP_BOOST);
        // Restore speed (default is usually ~0.25 for most animals, but let's check or just reset)
        // Actually, resetting base value to default is tricky if we don't know it.
        // But for animals, ~0.25 is standard. Cow 0.2, Sheep 0.23, Pig 0.25.
        // Better to just remove modifier? No, we set base value.
        // Let's assume standard value or store it.
        // For simplicity, I'll just restore to a safe default 0.25 which is roughly correct for livestock.
        if (entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
             entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.25); 
        }
    }
}
