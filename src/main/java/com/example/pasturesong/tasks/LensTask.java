package com.example.pasturesong.tasks;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.environment.EnvironmentManager.LoadResult;
import com.example.pasturesong.genetics.GeneData;
import com.example.pasturesong.genetics.Trait;
import com.example.pasturesong.listeners.ToolListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LensTask extends BukkitRunnable {

    private final PastureSong plugin;
    // Player UUID -> Display UUID
    private final Map<UUID, UUID> activeDisplays = new HashMap<>();
    // Player UUID -> Target Entity UUID (to detect switch)
    private final Map<UUID, UUID> currentTargets = new HashMap<>();
    // Player UUID -> Timestamp (When target was last seen)
    private final Map<UUID, Long> lastSeenTime = new HashMap<>();
    // Players in "Toggle Mode" (Show All)
    private final Set<UUID> toggleModePlayers = new HashSet<>();
    
    // Toggle Mode Displays: Player UUID -> Map<Entity UUID, Display UUID>
    private final Map<UUID, Map<UUID, UUID>> toggleDisplays = new HashMap<>();

    private static final long BUFFER_TIME = 2000; // 2 seconds
    private int tickCounter = 0;

    public LensTask(PastureSong plugin) {
        this.plugin = plugin;
        cleanupOrphanedDisplays();
    }
    
    // Cleanup stuck displays on startup
    private void cleanupOrphanedDisplays() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay) {
                    // Check tag
                    if (entity.getScoreboardTags().contains("ps_lens_display")) {
                        entity.remove();
                        continue;
                    }
                    
                    // Check text content (Legacy/Bugged cleanup)
                    // Remove ANY TextDisplay with our title, regardless of vehicle status
                    TextDisplay td = (TextDisplay) entity;
                    String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(td.text());
                    if (text != null && text.contains("牧场透镜")) {
                        entity.remove();
                    }
                }
            }
        }
    }

    public void toggleMode(Player player) {
        if (toggleModePlayers.contains(player.getUniqueId())) {
            toggleModePlayers.remove(player.getUniqueId());
            clearToggleDisplays(player);
            player.sendMessage("§e[牧场透镜] 已关闭全景模式。");
        } else {
            toggleModePlayers.add(player.getUniqueId());
            toggleDisplays.put(player.getUniqueId(), new HashMap<>());
            player.sendMessage("§a[牧场透镜] 已开启全景模式 (显示所有)。");
        }
    }

    private void handleNoteBlockHighlight(Player player) {
        var trace = player.rayTraceBlocks(10, FluidCollisionMode.NEVER);
        if (trace != null && trace.getHitBlock() != null) {
            Block block = trace.getHitBlock();
            if (block.getType() == Material.NOTE_BLOCK) {
                // Green note particle (approx pitch 12/24 = 0.5)
                player.spawnParticle(Particle.NOTE, 
                    block.getLocation().add(0.5, 1.2, 0.5), 
                    0, 0.5, 0, 0, 1);
            }
        }
    }

    @Override
    public void run() {
        tickCounter++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            handlePlayer(player);
        }
    }

    private void handlePlayer(Player player) {
        boolean hasLens = plugin.getItemManager().isCustomItem(player.getInventory().getItemInMainHand(), "GENETIC_LENS") ||
                          plugin.getItemManager().isCustomItem(player.getInventory().getItemInOffHand(), "GENETIC_LENS");

        if (!hasLens) {
            removeDisplay(player);
            if (toggleModePlayers.contains(player.getUniqueId())) {
                toggleModePlayers.remove(player.getUniqueId());
                clearToggleDisplays(player);
            }
            return;
        }

        // Environment Action Bar (Every 10 ticks)
        if (tickCounter % 10 == 0) {
            updateEnvironmentActionBar(player);
        }

        handleNoteBlockHighlight(player);
        // ... rest of logic

        if (toggleModePlayers.contains(player.getUniqueId())) {
            // Toggle Mode Logic
            handleToggleMode(player);
        } else {
            // Single Target Mode Logic
            handleSingleTargetMode(player);
        }
    }
    
    private void handleToggleMode(Player player) {
        // Clear single display if exists
        removeDisplay(player);
        
        Map<UUID, UUID> displays = toggleDisplays.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Set<UUID> currentEntities = new HashSet<>();
        
        // Scan nearby entities
        for (Entity entity : player.getNearbyEntities(15, 15, 15)) {
            if (entity instanceof LivingEntity && ToolListener.isLivestock((LivingEntity) entity)) {
                LivingEntity living = (LivingEntity) entity;
                currentEntities.add(living.getUniqueId());
                updateToggleDisplay(player, living, displays);
            }
        }
        
        // Remove displays for entities no longer in range
        displays.keySet().removeIf(uuid -> {
            if (!currentEntities.contains(uuid)) {
                Entity e = Bukkit.getEntity(displays.get(uuid));
                if (e != null) e.remove();
                return true;
            }
            return false;
        });
    }

    private void updateToggleDisplay(Player player, LivingEntity entity, Map<UUID, UUID> displays) {
        UUID displayId = displays.get(entity.getUniqueId());
        TextDisplay display = null;

        if (displayId != null) {
            Entity e = Bukkit.getEntity(displayId);
            if (e instanceof TextDisplay && !e.isDead()) {
                display = (TextDisplay) e;
            } else {
                displays.remove(entity.getUniqueId());
            }
        }

        if (display == null) {
            display = createDisplay(entity);
            display.setVisibleByDefault(false);
            player.showEntity(plugin, display); // Only visible to this player? 
            // TextDisplay visibleByDefault=false + showEntity doesn't work perfectly for passengers usually.
            // Better: use default visibility but Packets... or just set visibleByDefault=false and add player.
            // Spigot API: display.setVisibleByDefault(false); player.showEntity(plugin, display);
            // However, TextDisplay entities as passengers might be tricky.
            // Let's stick to standard spawn but try to hide from others? 
            // For now, simpler implementation: visible to everyone? User requested "Only visible to player".
            // Since we use addPassenger, the display is tied to the entity.
            // Hiding passengers for specific players is hard without ProtocolLib.
            // Alternative: Don't use addPassenger. Teleport constantly.
            // Or: accept that it might be visible to others for now, or use ProtocolLib if available (not available).
            // Wait, TextDisplay supports `setVisibleByDefault(false)` and `player.showEntity(...)`.
            // Let's try that.
            
            displays.put(entity.getUniqueId(), display.getUniqueId());
        }
        
        // Ensure visibility for specific player
        if (!display.isVisibleByDefault()) {
             player.showEntity(plugin, display);
        }

        display.text(Component.text(buildInfoText(entity)));
    }

    private void handleSingleTargetMode(Player player) {
        // Raytrace
        Entity target = null;
        var trace = player.rayTraceEntities(10); 
        if (trace != null && trace.getHitEntity() != null) {
            target = trace.getHitEntity();
        }

        if (target instanceof LivingEntity && ToolListener.isLivestock((LivingEntity) target)) {
            updateDisplay(player, (LivingEntity) target);
            lastSeenTime.put(player.getUniqueId(), System.currentTimeMillis());
        } else {
            // Buffer Check
            Long lastSeen = lastSeenTime.get(player.getUniqueId());
            if (lastSeen != null && System.currentTimeMillis() - lastSeen < BUFFER_TIME) {
                // Keep showing (do nothing, let it update if target is still valid in map)
                UUID currentTargetUUID = currentTargets.get(player.getUniqueId());
                if (currentTargetUUID != null) {
                    Entity cachedTarget = Bukkit.getEntity(currentTargetUUID);
                    if (cachedTarget instanceof LivingEntity && cachedTarget.isValid()) {
                         updateDisplay(player, (LivingEntity) cachedTarget);
                    } else {
                        removeDisplay(player);
                    }
                }
            } else {
                removeDisplay(player);
            }
        }
    }

    private TextDisplay createDisplay(LivingEntity entity) {
        TextDisplay display = entity.getWorld().spawn(entity.getLocation(), TextDisplay.class);
        display.setBillboard(Display.Billboard.CENTER);
        display.setVisibleByDefault(false); // Hide from everyone by default
        display.addScoreboardTag("ps_lens_display"); // Mark for cleanup
        entity.addPassenger(display);
        
        display.setTransformation(new Transformation(
            new Vector3f(0, 0.2f, 0), 
            new AxisAngle4f(), 
            new Vector3f(1, 1, 1), 
            new AxisAngle4f()
        ));
        return display;
    }

    private void updateDisplay(Player player, LivingEntity entity) {
        // Validation check
        if (!entity.isValid() || entity.isDead()) {
            removeDisplay(player);
            return;
        }

        UUID displayId = activeDisplays.get(player.getUniqueId());
        UUID targetId = currentTargets.get(player.getUniqueId());

        TextDisplay display = null;

        // Check if we need to create a new display
        if (displayId != null) {
            Entity e = Bukkit.getEntity(displayId);
            if (e instanceof TextDisplay && !e.isDead() && entity.getUniqueId().equals(targetId)) {
                display = (TextDisplay) e;
            } else {
                removeDisplay(player);
            }
        }

        // Create if null
        if (display == null) {
            display = createDisplay(entity);
            activeDisplays.put(player.getUniqueId(), display.getUniqueId());
            currentTargets.put(player.getUniqueId(), entity.getUniqueId());
        }
        
        // Ensure visibility
        player.showEntity(plugin, display);

        // Update Text content
        display.text(Component.text(buildInfoText(entity)));
    }

    private void removeDisplay(Player player) {
        UUID displayId = activeDisplays.remove(player.getUniqueId());
        currentTargets.remove(player.getUniqueId());
        
        if (displayId != null) {
            Entity e = Bukkit.getEntity(displayId);
            if (e != null) {
                e.remove();
            }
        }
    }
    
    private void clearToggleDisplays(Player player) {
        Map<UUID, UUID> displays = toggleDisplays.remove(player.getUniqueId());
        if (displays != null) {
            for (UUID uuid : displays.values()) {
                Entity e = Bukkit.getEntity(uuid);
                if (e != null) e.remove();
            }
        }
    }

    private String buildInfoText(LivingEntity entity) {
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(entity);
        if (genes == null) return "§c无基因数据";

        StringBuilder text = new StringBuilder("§6§l=== 牧场透镜 ===\n");
        
        // 1. Status
        double stress = plugin.getStressManager().getTotalStress(entity);
        double baseStress = plugin.getStressManager().calculateBaseStress(entity);
        double maxStress = plugin.getStressManager().getMaxStress();
        
        String stressColor = (stress < 20) ? "§a" : (stress < 50 ? "§e" : "§c");
        // Format: 应激: 86 (30/100)
        // 30 is base, 100 is max. stress is total.
        // Wait, total = base + temp.
        // So showing (base/max) is a bit confusing if total > base.
        // Maybe "Total (Base+Temp / Max)"?
        // User asked for: "应激：86（30/100）", where 30 is base, 100 is max.
        text.append(String.format("§f应激： %s%.0f §7（%.0f/%.0f）\n", stressColor, stress, baseStress, maxStress));
        
        if (plugin.getDiseaseManager().isSick((org.bukkit.entity.Animals)entity)) {
            text.append("§c☣ 已感染 ☣\n");
        }
        if (plugin.getExhaustionManager().isExhausted(entity)) {
            long remaining = plugin.getExhaustionManager().getRemainingTime(entity) / 1000 / 60;
            text.append(String.format("§7⚠ 疲劳 (%d min)\n", remaining));
        }
        
        // 2. Environment (Removed - Moved to Action Bar)
        
        text.append("§6--- 性状特征 ---\n");
        for (Trait trait : Trait.values()) {
            double score = genes.getGenePair(trait).getPhenotypeValue();
            String adj = trait.getAdjective(score);
            if (adj == null) continue;
            
            String color = (score > 0) ? "§a" : "§c";
            text.append(String.format("§f%s: %s%s\n", trait.getName(), color, adj));
        }
        
        return text.toString();
    }

    private void updateEnvironmentActionBar(Player player) {
        LoadResult load = plugin.getEnvironmentManager().getLoad(player);
        if (load.isValid && !load.isOpenWorld) {
            String status = load.overloaded ? "§c拥挤" : "§a舒适";
            String msg = String.format("§e[牧场] §f状态: %s §7(%.0f/%.0f)", status, load.currentLoad, load.capacity);
            player.sendActionBar(Component.text(msg));
        }
    }
}
