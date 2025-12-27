package com.example.pasturesong.qte;

import com.example.pasturesong.PastureSong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CowMilkingQTE extends QTE {
    private final List<Boolean> sequence = new ArrayList<>(); // true = Left, false = Right
    private int currentIndex = 0;
    private int maxTime = 100; // 5 seconds
    private int tick = 0;

    private final LivingEntity cow;

    public CowMilkingQTE(PastureSong plugin, Player player, LivingEntity cow, Runnable onSuccess, Runnable onFailure) {
        super(plugin, player, onSuccess, onFailure);
        this.cow = cow;
    }

    @Override
    protected void onStart() {
        freezeEntity(cow);
        // Generate sequence of 4-6 inputs
        int len = ThreadLocalRandom.current().nextInt(4, 7);
        for (int i = 0; i < len; i++) {
            sequence.add(ThreadLocalRandom.current().nextBoolean());
        }
        updateDisplay();
        player.playSound(player.getLocation(), Sound.ENTITY_COW_AMBIENT, 1f, 1f);
    }

    @Override
    protected void onTick() {
        tick++;
        // double timeLeft = (maxTime - tick) / 20.0; // Unused variable removed
        
        if (tick > maxTime) {
            player.sendMessage("§c超时！");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            fail();
        } else {
             // Update Timer Display
             updateDisplay();
        }
    }

    @Override
    protected void onEnd() {
        unfreezeEntity(cow);
        player.clearTitle();
    }

    @Override
    public void onLeftClick() {
        handleInput(true);
    }

    @Override
    public void onRightClick() {
        handleInput(false);
    }

    private void handleInput(boolean isLeft) {
        if (sequence.get(currentIndex) == isLeft) {
            currentIndex++;
            // Feedback: Note sound + Particle
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f + (currentIndex * 0.1f));
            cow.getWorld().spawnParticle(org.bukkit.Particle.NOTE, cow.getLocation().add(0, 1.5, 0), 1, 0.5, 0.5, 0.5);
            
            if (currentIndex >= sequence.size()) {
                // Final Success Feedback
                cow.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, cow.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                success();
            } else {
                updateDisplay();
            }
        } else {
            // Failure Feedback
            player.sendMessage("§c节奏错误！");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            cow.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, cow.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2);
            fail();
        }
    }

    private void updateDisplay() {
        // Current Target
        boolean nextIsLeft = sequence.get(currentIndex);
        String titleText = nextIsLeft ? "§b§l[左键]" : "§6§l[右键]";
        
        // Timer
        double timeLeft = Math.max(0, (maxTime - tick) / 20.0);
        String subTitleText = String.format("§7剩余时间: %.1fs", timeLeft);

        Title title = Title.title(
            Component.text(titleText),
            Component.text(subTitleText),
            Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ofMillis(0))
        );
        player.showTitle(title);
        
        // ActionBar Sequence
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sequence.size(); i++) {
            if (i == currentIndex) sb.append("§e[NOW] ");
            else if (i < currentIndex) sb.append("§a[✔] ");
            else sb.append("§7[?] ");
        }
        player.sendActionBar(Component.text(sb.toString()));
    }
}
