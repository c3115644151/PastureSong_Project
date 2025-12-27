package com.example.pasturesong.qte;

import com.example.pasturesong.PastureSong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class PigTruffleQTE extends QTE {
    private final LivingEntity pig;
    private int foundCount = 0;
    private final int targetCount = 3;
    
    private State state = State.IDLE;
    private Location targetLocation;
    private int waitTicks = 0;
    private int totalDuration = 0;
    
    private enum State {
        IDLE,
        MOVING,
        DIGGING
    }

    private final Runnable onDig;

    public PigTruffleQTE(PastureSong plugin, Player player, LivingEntity pig, Runnable onDig, Runnable onSuccess, Runnable onFailure) {
        super(plugin, player, onSuccess, onFailure);
        this.pig = pig;
        this.onDig = onDig;
    }

    @Override
    protected void onStart() {
        player.sendMessage("§e[嗅觉雷达] §7猪开始了寻宝！跟随它！");
        if (pig instanceof Mob) {
            ((Mob) pig).setTarget(null);
        }
    }

    @Override
    protected void onTick() {
        if (pig == null || !pig.isValid()) {
            fail();
            return;
        }
        
        totalDuration++;
        if (totalDuration > 1200) { // 60 seconds max
            player.sendMessage("§c猪累了，停止了搜寻。");
            success(); // Treat as partial success or just end
            return;
        }

        switch (state) {
            case IDLE:
                waitTicks++;
                if (waitTicks > 40) { // Wait 2 seconds before finding next spot
                    findNextSpot();
                }
                break;
                
            case MOVING:
                if (targetLocation == null) {
                    state = State.IDLE;
                    return;
                }
                
                // Visuals
                if (totalDuration % 10 == 0) {
                     pig.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, pig.getLocation().add(0, 1, 0), 1);
                }
                
                // Check if reached
                if (pig.getLocation().distance(targetLocation) < 1.5) {
                    state = State.DIGGING;
                    waitTicks = 0;
                    // Stop moving
                    if (pig instanceof Mob) {
                        ((Mob) pig).getPathfinder().stopPathfinding();
                    }
                } else {
                    // Keep moving (sometimes pathfinder resets)
                    if (totalDuration % 20 == 0 && pig instanceof Mob) {
                         ((Mob) pig).getPathfinder().moveTo(targetLocation);
                    }
                    
                    // Stuck check
                    if (pig.getLocation().distance(targetLocation) > 15) {
                        // Teleport if too far/stuck? Or just fail spot
                        pig.teleport(targetLocation); // Cheating a bit to ensure flow
                    }
                }
                break;
                
            case DIGGING:
                waitTicks++;
                // Dig animation effects
                if (waitTicks % 5 == 0) {
                    pig.getWorld().playSound(pig.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1f, 0.8f);
                    pig.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, pig.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
                }
                
                if (waitTicks > 60) { // Dig for 3 seconds
                    dropTruffle();
                    foundCount++;
                    
                    if (foundCount >= targetCount) {
                        player.sendMessage("§a猪看起来心满意足！");
                        success();
                    } else {
                        state = State.IDLE;
                        waitTicks = 0;
                        player.sendMessage("§e它似乎闻到了更多...");
                    }
                }
                break;
        }
    }

    private void findNextSpot() {
        Location loc = findRandomSpot();
        if (loc != null) {
            targetLocation = loc;
            state = State.MOVING;
            waitTicks = 0;
            
            pig.getWorld().spawnParticle(Particle.HEART, pig.getLocation().add(0, 1.5, 0), 5);
            pig.getWorld().playSound(pig.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1f, 1.5f);
            
            // Move
            if (pig instanceof Mob) {
                ((Mob) pig).getPathfinder().moveTo(targetLocation);
            }
        } else {
            // No spot found, end
            player.sendMessage("§c附近似乎没有更多宝藏了。");
            success();
        }
    }
    
    private Location findRandomSpot() {
        for (int i = 0; i < 10; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double dist = 3 + ThreadLocalRandom.current().nextDouble() * 7; // 3-10 blocks
            double x = pig.getLocation().getX() + Math.cos(angle) * dist;
            double z = pig.getLocation().getZ() + Math.sin(angle) * dist;
            int y = pig.getLocation().getBlockY();
            
            // Adjust Y
            Location tryLoc = new Location(pig.getWorld(), x, y, z);
            Block block = tryLoc.getBlock();
            
            // Check ground
            Block ground = block.getRelative(0, -1, 0);
            if (isValidSoil(ground.getType()) && block.getType() == Material.AIR && block.getRelative(0, 1, 0).getType() == Material.AIR) {
                return tryLoc;
            }
        }
        return null;
    }
    
    private void dropTruffle() {
        if (onDig != null) {
            onDig.run();
        }
    }

    private boolean isValidSoil(Material mat) {
        return mat == Material.GRASS_BLOCK || mat == Material.DIRT || mat == Material.PODZOL || mat == Material.MYCELIUM || mat == Material.COARSE_DIRT || mat == Material.ROOTED_DIRT;
    }

    @Override
    protected void onEnd() {
        if (pig instanceof Mob) {
            ((Mob) pig).getPathfinder().stopPathfinding();
        }
    }
}
