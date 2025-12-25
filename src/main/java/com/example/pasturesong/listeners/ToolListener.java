package com.example.pasturesong.listeners;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.genetics.GeneData;
import com.example.pasturesong.genetics.Trait;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class ToolListener implements Listener {

    private final PastureSong plugin;

    public ToolListener(PastureSong plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getRightClicked();
        
        // Only Livestock
        if (!isLivestock(entity)) return;
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // 1. DNA Sampler
        if (plugin.getItemManager().isCustomItem(item, "DNA_SAMPLER")) { 
            handleDNASample(player, entity);
            event.setCancelled(true);
        }
        
        // 2. Genetic Lens
        if (plugin.getItemManager().isCustomItem(item, "GENETIC_LENS")) { 
            handleLensView(player, entity);
            event.setCancelled(true);
        }
    }
    
    private boolean isLivestock(LivingEntity entity) {
        return entity instanceof org.bukkit.entity.Cow || 
               entity instanceof org.bukkit.entity.Sheep || 
               entity instanceof org.bukkit.entity.Pig || 
               entity instanceof org.bukkit.entity.Chicken || 
               entity instanceof org.bukkit.entity.Horse;
    }

    private void handleDNASample(Player player, LivingEntity entity) {
        // Damage & Stress
        entity.damage(1.0); // 0.5 heart
        plugin.getStressManager().addTemporaryStress(entity, 15.0);
        player.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
        
        // Create DNA Sample
        ItemStack sample = new ItemStack(Material.PAPER); 
        ItemMeta meta = sample.getItemMeta();
        meta.displayName(Component.text("§bDNA 样本 (未鉴定)"));
        sample.setItemMeta(meta);
        
        // Save Genes to Item
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(entity);
        genes.setIdentified(false); // Force unidentified
        plugin.getGeneticsManager().saveGenesToItem(sample, genes);
        plugin.getGeneticsManager().updateGeneLore(sample, genes);
        
        // Give to player
        player.getInventory().addItem(sample);
        player.sendMessage("§a成功提取 DNA 样本！");
    }

    private void handleLensView(Player player, LivingEntity entity) {
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(entity);
        if (genes == null) return;
        
        // Spawn Text Display
        TextDisplay display = entity.getWorld().spawn(entity.getLocation().add(0, entity.getHeight() + 0.5, 0), TextDisplay.class);
        display.setBillboard(Display.Billboard.CENTER);
        
        StringBuilder text = new StringBuilder("§6§l=== 基因性状 ===\n");
        for (Trait trait : Trait.values()) {
            double score = genes.getGenePair(trait).getPhenotypeValue();
            String adj = trait.getAdjective(score);
            if (adj == null) continue;
            
            String color = (score > 0) ? "§a" : "§c";
            text.append(String.format("§f%s: %s%s\n", trait.getName(), color, adj));
        }
        
        display.text(Component.text(text.toString()));
        entity.addPassenger(display);
        
        // Adjust position (ride offset)
        display.setTransformation(new Transformation(
            new Vector3f(0, (float)entity.getHeight() + 0.5f, 0), 
            new AxisAngle4f(), 
            new Vector3f(1, 1, 1), 
            new AxisAngle4f()
        ));
        
        // Remove after 5 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                display.remove();
            }
        }.runTaskLater(plugin, 100L); // 5 seconds
        
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
    }
}