package com.example.pasturesong.listeners;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.genetics.GeneData;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.block.Block;
// import org.bukkit.Material; // Unused
import org.bukkit.inventory.ItemStack;

public class ToolListener implements Listener {

    private final PastureSong plugin;

    public ToolListener(PastureSong plugin) {
        this.plugin = plugin;
    }

    // 2. Genetic Lens Toggle (Left Click Air/Block) -> REMOVED in favor of Swap Item (F)
    /*
    @EventHandler
    public void onInteractBlock(PlayerInteractEvent event) {
        // ...
    }
    */
    // We will clean up the previous left-click logic and replace with SwapHandItemsEvent

    @EventHandler
    public void onInteractBlock(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 1. Genetic Lens Toggle (Sneak + Left Click Air/Block)
        if (player.isSneaking() && 
           (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            
            // Check Main Hand or Off Hand
            boolean mainHand = plugin.getItemManager().isCustomItem(player.getInventory().getItemInMainHand(), "GENETIC_LENS");
            boolean offHand = plugin.getItemManager().isCustomItem(player.getInventory().getItemInOffHand(), "GENETIC_LENS");
            
            if (mainHand || offHand) {
                com.example.pasturesong.tasks.LensTask task = getLensTask();
                if (task != null) {
                    task.toggleMode(player);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                }
                event.setCancelled(true); // Prevent breaking block
                return;
            }
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        if (item == null) return;
        
        // Manure/Shovel logic moved to ManureListener
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return; // Only Main Hand Trigger
        
        if (!(event.getRightClicked() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getRightClicked();
        
        // Only Livestock
        if (!isLivestock(entity)) return;
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // 1. DNA Sampler
        if (plugin.getItemManager().isCustomItem(item, "DNA_SAMPLER")) { 
            handleDNASample(player, entity, item);
            event.setCancelled(true);
        }
        
        // 2. Genetic Lens (Right Click Entity -> Cancel to prevent zoom if possible, or ignore)
        if (plugin.getItemManager().isCustomItem(item, "GENETIC_LENS")) { 
            // We moved toggle to Left Click in onInteractBlock
            // Here we just cancel interaction to prevent spyglass zoom if possible?
            // Actually spyglass zoom is client-side prediction + server confirmation.
            // Cancelling here might stop it.
            // But user said "Right click uses spyglass". So we let it use spyglass (zoom).
            // We do nothing here.
            return;
        }
    }
    
    private com.example.pasturesong.tasks.LensTask getLensTask() {
        // This is a bit hacky, but we need reference to the task. 
        // Ideally PastureSong main class should expose it.
        // Assuming we can get it via static or passed in constructor if modified.
        // For now, let's assume PastureSong has a getter or we stored it.
        // Wait, PastureSong.java didn't expose the task instance.
        // I need to update PastureSong.java to expose the LensTask instance.
        return plugin.getLensTask();
    }
    
    public static boolean isLivestock(LivingEntity entity) {
        return entity instanceof org.bukkit.entity.Cow || 
               entity instanceof org.bukkit.entity.Sheep || 
               entity instanceof org.bukkit.entity.Pig || 
               entity instanceof org.bukkit.entity.Chicken || 
               entity instanceof org.bukkit.entity.Horse;
    }

    private void handleDNASample(Player player, LivingEntity entity, ItemStack tool) {
        // We use 'tool' only for initial check. For modification we fetch from hand and clone.
        
        // Check if full
        if (plugin.getGeneticsManager().hasGeneData(tool)) {
            plugin.getGeneticsManager().clearGeneData(tool);
            player.playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
            player.sendMessage("§e已清空 DNA 采样器。");
            // Update inventory
            player.getInventory().setItemInMainHand(tool);
            return;
        }

        // Damage & Stress
        entity.damage(1.0); // 0.5 heart
        plugin.getStressManager().addTemporaryStress(entity, 15.0);
        player.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
        
        // Get Genes
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(entity);
        genes.setIdentified(false); // Force unidentified
        
        // CRITICAL FIX: Use clone and set back to ensure item update
        ItemStack handItem = player.getInventory().getItemInMainHand();
        ItemStack newItem = handItem.clone();
        
        plugin.getGeneticsManager().saveGenesToItem(newItem, genes);
        plugin.getGeneticsManager().setGeneSource(newItem, entity);
        plugin.getGeneticsManager().updateGeneLore(newItem, genes);
        
        // Explicitly set back to hand
        player.getInventory().setItemInMainHand(newItem);
        
        player.sendMessage("§a成功提取 DNA 样本！");
    }
}