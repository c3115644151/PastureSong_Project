package com.example.pasturesong.production;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.environment.EnvironmentManager;
import com.example.pasturesong.genetics.GeneData;
import com.example.pasturesong.genetics.Trait;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ProductionListener implements Listener {

    private final PastureSong plugin;

    public ProductionListener(PastureSong plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Animals)) return;
        Animals animal = (Animals) event.getEntity();
        
        // Only affect adult animals? Or all? Usually adults produce more.
        // Let's assume all for now or check age.
        
        List<ItemStack> drops = event.getDrops();
        List<ItemStack> newDrops = new ArrayList<>();
        
        double stress = plugin.getStressManager().getTotalStress(animal);
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(animal);
        double fertility = 0;
        if (genes != null) {
            fertility = genes.getGenePair(Trait.FERTILITY).getPhenotypeValue();
        }
        
        // Stress Multiplier
        double stressMult = 1.0;
        if (stress <= 0.1) stressMult = 1.25;
        else if (stress <= 20) stressMult = 1.0;
        else if (stress <= 50) stressMult = 0.7;
        else if (stress <= 70) stressMult = 0.5;
        else if (stress <= 90) stressMult = 0.3;
        else stressMult = 0.1;
        
        for (ItemStack drop : drops) {
            int originalAmount = drop.getAmount();
            // Base 10x
            int baseAmount = originalAmount * 10;
            
            // Fertility Adjustment
            int fAdjustment = 0;
            int fScore = (int) Math.abs(fertility); // Assuming int steps for loop
            int sign = (fertility >= 0) ? 1 : -1;
            
            for (int i = 0; i < fScore; i++) {
                if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                    fAdjustment += sign;
                }
            }
            
            int amount = baseAmount + fAdjustment;
            if (amount < 0) amount = 0;
            
            // Apply Stress Multiplier
            amount = (int) (amount * stressMult);
            
            if (amount > 0) {
                drop.setAmount(amount);
                newDrops.add(drop);
            }
        }
        
        // Update drops
        // We can't modify the list directly if we are iterating, but here we created new list
        // Actually event.getDrops() is mutable.
        // But better to clear and addall?
        // Or just modify the items in place if we didn't clone them?
        // We modified 'drop' amount.
        // But if we filtered out some...
        // Let's just rely on the modification.
    }

    @EventHandler
    public void onCowInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Cow)) return;
        Cow cow = (Cow) event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check for Glass Bottle (Specialty Milking)
        if (item.getType() == Material.GLASS_BOTTLE) {
            handleSpecialtyHarvest(event, cow, item, "特产奶", Sound.ENTITY_COW_MILK);
        }
        // Bucket handling (Vanilla) - check exhaustion
        else if (item.getType() == Material.BUCKET) {
            if (plugin.getExhaustionManager().isExhausted(cow)) {
                 plugin.getStressManager().addTemporaryStress(cow, 20.0);
                 player.sendMessage("§c它很累，产奶量可能受影响。(应激值上升)");
            }
        }
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onShear(PlayerShearEntityEvent event) {
        if (!(event.getEntity() instanceof Sheep)) return;
        Sheep sheep = (Sheep) event.getEntity();
        Player player = event.getPlayer();
        
        // Check Exhaustion
        if (plugin.getExhaustionManager().isExhausted(sheep)) {
            double penalty = plugin.getConfig().getDouble("stress.interaction_penalty", 20.0);
            plugin.getStressManager().addTemporaryStress(sheep, penalty);
            player.sendMessage("§c这就去剪毛吗？它看起来很累。(应激值上升)");
        } 
        
        String specialtyItemKey = getSpecialtyDrop(sheep);
        EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(sheep);
        
        if (specialtyItemKey != null && load.isValid && !plugin.getExhaustionManager().isExhausted(sheep)) {
            // Potential Specialty Harvest
            event.setCancelled(true);
            
            // QTE Logic (Placeholder)
            player.sendMessage("§a[QTE Placeholder] 剪毛QTE成功！");
            
            // Calculate Amount
            double multiplier = getSpecialtyGeneMultiplier(sheep);
            int amount = (int) multiplier;
            if (ThreadLocalRandom.current().nextDouble() < (multiplier - amount)) {
                amount++;
            }
            if (amount < 1) amount = 1;
            
            // Drop Specialty
            ItemStack result = getSpecialtyItemStack(specialtyItemKey);
            if (result != null) {
                result.setAmount(amount);
                applyQualityLore(result, getQualityStars(sheep));
                sheep.getWorld().dropItemNaturally(sheep.getLocation(), result);
                player.sendMessage("§b获得特产: " + result.getItemMeta().getDisplayName() + " x" + amount);
            }
            
            // Apply Exhaustion
            plugin.getExhaustionManager().setExhausted(sheep, 15 * 60 * 1000L);
            sheep.setSheared(true); 
            player.playSound(sheep.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.0f, 1.0f);
            
            // Damage Tool
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().name().contains("SHEARS")) {
                hand.damage(1, player);
            }
        }
    }

    @EventHandler
    public void onChickenInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Chicken)) return;
        Chicken chicken = (Chicken) event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.BRUSH) {
            handleSpecialtyHarvest(event, chicken, item, "特产羽毛", Sound.ENTITY_CHICKEN_HURT); 
        }
    }

    @EventHandler
    public void onPigInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Pig)) return;
        Pig pig = (Pig) event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.GOLDEN_SHOVEL) {
            handleSpecialtyHarvest(event, pig, item, "特产松露", Sound.ITEM_SHOVEL_FLATTEN);
        }
    }
    
    private void handleSpecialtyHarvest(PlayerInteractEntityEvent event, LivingEntity entity, ItemStack tool, String name, Sound sound) {
        Player player = event.getPlayer();
        
        EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(entity);
        if (!load.isValid) {
            player.sendMessage("§c只有在健康的牧场中，才能获取" + name + "。");
            event.setCancelled(true);
            return;
        }

        if (plugin.getExhaustionManager().isExhausted(entity)) {
            event.setCancelled(true);
            player.sendMessage("§c动物太累了。(应激值上升)");
            plugin.getStressManager().addTemporaryStress(entity, 10.0);
            return;
        }

        String specialtyKey = getSpecialtyDrop(entity);
        if (specialtyKey == null) {
            player.sendMessage("§c此处的水土养不出" + name + "。");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        player.sendMessage("§a[QTE Placeholder] 成功获取" + name);
        
        double multiplier = getSpecialtyGeneMultiplier(entity);
        int amount = (int) multiplier;
        if (ThreadLocalRandom.current().nextDouble() < (multiplier - amount)) {
            amount++;
        }
        if (amount < 1) amount = 1;
        
        ItemStack result = getSpecialtyItemStack(specialtyKey);
        if (result != null) {
            result.setAmount(amount);
            applyQualityLore(result, getQualityStars(entity));
            entity.getWorld().dropItemNaturally(entity.getLocation(), result);
        } else {
             player.sendMessage("§cError: Item not found (" + specialtyKey + ")");
        }
        
        plugin.getExhaustionManager().setExhausted(entity, 15 * 60 * 1000L);
        player.playSound(entity.getLocation(), sound, 1.0f, 1.0f);
        
        tool.damage(1, player);
    }
    
    // --- Helpers ---

    private String getSpecialtyDrop(LivingEntity entity) {
        Biome biome = entity.getLocation().getBlock().getBiome();
        // Simplified Biome check
        boolean isPlains = biome.name().contains("PLAINS") || biome.name().contains("MEADOW");
        boolean isForest = biome.name().contains("FOREST");
        boolean isJungle = biome.name().contains("JUNGLE");
        boolean isCold = biome.name().contains("SNOW") || biome.name().contains("ICE");
        
        if (entity instanceof Sheep) {
            if (isPlains) return "HIGHLAND_WOOL";
            if (isCold) return "FROST_FLEECE";
        } else if (entity instanceof Cow) {
            if (isPlains) return "RICH_MILK";
            if (isForest) return "HERBAL_MILK";
        } else if (entity instanceof Pig) {
            if (isForest) return "TRUFFLE";
        } else if (entity instanceof Chicken) {
            if (isJungle || isForest) return "PHEASANT_FEATHER";
        }
        return null; // No specialty here
    }

    private double getSpecialtyGeneMultiplier(LivingEntity entity) {
        if (!(entity instanceof Animals)) return 1.0;
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(entity);
        if (genes == null) return 1.0;
        double f = genes.getGenePair(Trait.FERTILITY).getPhenotypeValue();
        // Formula: 1.0 + (Score / 10.0)
        // Score 14 -> 2.4x
        return 1.0 + (f / 10.0);
    }

    private int getQualityStars(LivingEntity entity) {
        if (!(entity instanceof Animals)) return 1;
        Animals animal = (Animals) entity;
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(entity);
        if (genes == null) return 1;
        
        double q = genes.getGenePair(Trait.QUALITY).getPhenotypeValue();
        
        // Avg of others
        double v = genes.getGenePair(Trait.VITALITY).getPhenotypeValue();
        double r = genes.getGenePair(Trait.RESISTANCE).getPhenotypeValue();
        double f = genes.getGenePair(Trait.FERTILITY).getPhenotypeValue();
        double avgOthers = (v + r + f) / 3.0;
        
        double score = (q * 0.5) + (avgOthers * 0.5);
        
        int stars = 1;
        if (score >= 8.0) stars = 5;
        else if (score >= 2.0) stars = 4;
        else if (score >= -2.0) stars = 3;
        else if (score >= -8.0) stars = 2;
        else stars = 1;
        
        // Stress Penalty
        double stress = plugin.getStressManager().getTotalStress(animal);
        if (stress > 99) stars = 1;
        else {
            if (stress > 70) stars -= 2;
            else if (stress > 30) stars -= 1;
        }
        
        if (stars < 1) stars = 1;
        return stars;
    }

    private ItemStack getSpecialtyItemStack(String key) {
        Material mat = Material.PAPER;
        String name = "未知特产";
        
        switch (key) {
            case "HIGHLAND_WOOL": mat = Material.WHITE_WOOL; name = "§b高地羊毛"; break;
            case "FROST_FLEECE": mat = Material.LIGHT_BLUE_WOOL; name = "§b霜冻羊毛"; break;
            case "RICH_MILK": mat = Material.MILK_BUCKET; name = "§b醇香牛乳"; break;
            case "HERBAL_MILK": mat = Material.MILK_BUCKET; name = "§b百草乳"; break;
            case "TRUFFLE": mat = Material.BROWN_MUSHROOM; name = "§b黑松露"; break;
            case "PHEASANT_FEATHER": mat = Material.FEATHER; name = "§b锦鸡翎"; break;
        }
        
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyQualityLore(ItemStack item, int stars) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        
        StringBuilder starStr = new StringBuilder("§6");
        for (int i = 0; i < 5; i++) {
            if (i < stars) starStr.append("★");
            else starStr.append("☆");
        }
        
        lore.add(0, Component.text(starStr.toString()));
        meta.lore(lore);
        item.setItemMeta(meta);
    }
}