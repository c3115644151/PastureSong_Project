package com.example.pasturesong.production;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.environment.EnvironmentManager;
import com.example.pasturesong.genetics.GeneData;
import com.example.pasturesong.genetics.Trait;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;

import com.example.pasturesong.qte.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import java.util.regex.Pattern;

import com.biomegifts.BiomeGifts;
import com.biomegifts.ConfigManager.ResourceConfig;

public class ProductionListener implements Listener {

    private final PastureSong plugin;
    private final java.util.Map<java.util.UUID, Long> interactionCooldowns = new java.util.HashMap<>();

    public ProductionListener(PastureSong plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Animals))
            return;
        if (!(event.getDamager() instanceof Player))
            return;

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        // Check Butchery Enchantment
        int level = com.example.pasturesong.enchantments.ButcheryEnchantment.getLevel(weapon, plugin);
        if (level > 0) {
            // Massive Damage Bonus against Animals
            // Base Damage: 21 (300% of Diamond Sword base 7)
            // Plus Level * 10

            double baseBonus = 14.0; // 7 + 14 = 21 (Total 300% or +200%)
            double levelBonus = level * 10.0;

            event.setDamage(event.getDamage() + baseBonus + levelBonus);

            // Effect
            player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, event.getEntity().getLocation().add(0, 1, 0), 10);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Animals))
            return;
        Animals animal = (Animals) event.getEntity();

        // Debug
        // plugin.getLogger().info("Processing death drops for " + animal.getType());

        try {
            // Clear vanilla drops
            event.getDrops().clear();
            event.setDroppedExp(0); // Optional: Custom EXP?

            List<ItemStack> newDrops = new ArrayList<>();
            boolean isBurning = animal.getFireTicks() > 0;

            // Define Base Drops (Reduced as requested)
            if (animal instanceof Cow) {
                addDrops(newDrops, isBurning ? Material.COOKED_BEEF : Material.BEEF, 20, 42);
                addDrops(newDrops, Material.LEATHER, 6, 12);
            } else if (animal instanceof Pig) {
                addDrops(newDrops, isBurning ? Material.COOKED_PORKCHOP : Material.PORKCHOP, 18, 30);
            } else if (animal instanceof Sheep) {
                addDrops(newDrops, isBurning ? Material.COOKED_MUTTON : Material.MUTTON, 15, 30);
                addDrops(newDrops, convertWoolColor(((Sheep) animal).getColor()), 2, 4);
            } else if (animal instanceof Chicken) {
                addDrops(newDrops, isBurning ? Material.COOKED_CHICKEN : Material.CHICKEN, 3, 6);
                addDrops(newDrops, Material.FEATHER, 6, 14);
            }

            double stress = plugin.getStressManager().getTotalStress(animal);
            GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(animal);
            double fertility = 0;
            // Growth Potential (Meat Amount)
            // double growth = 0; // Not used in new formula

            if (genes != null) {
                fertility = genes.getGenePair(Trait.FERTILITY).getPhenotypeValue();
                // growth = genes.getGenePair(Trait.VITALITY).getPhenotypeValue();
            }

            // 1. Butchery Bonus (Multiplier)
            // Check Killer
            double butcheryBonus = 0.0;
            Player killer = animal.getKiller();
            if (killer != null) {
                ItemStack weapon = killer.getInventory().getItemInMainHand();
                int level = com.example.pasturesong.enchantments.ButcheryEnchantment.getLevel(weapon, plugin);
                if (level > 0) {
                    // Level 1: 25%, Level 2: 50%, Level 3: 75%
                    butcheryBonus = level * 0.25;
                }
            }

            // 2. Stress Modifier (Multiplier)
            double stressMod = 0.0;
            if (stress <= 0.1) {
                stressMod = 0.25; // Perfect: +25%
            } else if (stress <= 20) {
                stressMod = 0.0; // Normal: +0%
            } else if (stress <= 50) {
                stressMod = -0.3; // Light: -30%
            } else if (stress <= 70) {
                stressMod = -0.5; // Moderate: -50%
            } else if (stress <= 90) {
                stressMod = -0.7; // Severe: -70%
            } else {
                stressMod = -0.9; // Collapse: -90%
            }

            // 3. Looting (Additive)
            int lootingAdd = 0;
            if (killer != null) {
                lootingAdd = killer.getInventory().getItemInMainHand()
                        .getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOTING);
            }

            // 4. Fertility (Multiplier)
            // Formula: Base * (1 + Butchery + Stress) * (1 + Fertility) + Looting
            // Fertility: Each point = 10%? User said "1 + Fertility Gene Bonus".
            // Let's assume Fertility Value * 0.1 (e.g. 5 -> 0.5 -> 1.5x)
            double fertilityBonus = 0.0;
            if (fertility > 0) {
                fertilityBonus = fertility * 0.1;
            } else {
                // Negative fertility reduces drops? Or just 0 bonus?
                // "1 + Bonus". If negative, it might be penalty.
                // Let's allow negative.
                fertilityBonus = fertility * 0.1;
            }

            // Calculate Base Multiplier
            double baseMultiplier = 1.0 + butcheryBonus + stressMod;
            if (baseMultiplier < 0)
                baseMultiplier = 0;

            // Calculate Fertility Multiplier
            double fertilityMultiplier = 1.0 + fertilityBonus;
            if (fertilityMultiplier < 0)
                fertilityMultiplier = 0;

            int stars = getQualityStars(animal);

            // Process Drops
            for (ItemStack drop : newDrops) {
                int baseAmount = drop.getAmount();

                // Formula: Base * (1 + Butchery + Stress) * (1 + Fertility) + Looting
                int amount = (int) (baseAmount * baseMultiplier * fertilityMultiplier + lootingAdd);

                if (amount > 0) {
                    drop.setAmount(amount);
                    applyQualityLore(drop, stars);
                    event.getDrops().add(drop);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Removed old getButcheryLevel method as we use Enchantment helper now

    private void addDrops(List<ItemStack> drops, Material material, int min, int max) {
        if (material == null)
            return;
        int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        if (amount > 0) {
            drops.add(new ItemStack(material, amount));
        }
    }

    private Material convertWoolColor(org.bukkit.DyeColor color) {
        if (color == null)
            return Material.WHITE_WOOL;
        try {
            return Material.valueOf(color.name() + "_WOOL");
        } catch (IllegalArgumentException e) {
            return Material.WHITE_WOOL;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCowInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (!(event.getRightClicked() instanceof Cow))
            return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (now - interactionCooldowns.getOrDefault(player.getUniqueId(), 0L) < 200)
            return;
        interactionCooldowns.put(player.getUniqueId(), now);

        Cow cow = (Cow) event.getRightClicked();

        // Prevent QTE conflict
        if (plugin.getQTEManager().isQTEActive(player)) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        // Check for Glass Bottle (Specialty Milking)
        if (item.getType() == Material.GLASS_BOTTLE) {
            EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(cow);
            String specialty = getSpecialtyDrop(cow);

            if (specialty == null || !load.isValid || plugin.getExhaustionManager().isExhausted(cow)) {
                handleSpecialtyHarvest(event, cow, item, "特产奶", Sound.ENTITY_COW_MILK);
                return;
            }

            // Start QTE
            event.setCancelled(true);
            plugin.getQTEManager().startQTE(player, new CowMilkingQTE(plugin, player, cow,
                    () -> doSpecialtyHarvest(player, cow, item, specialty, Sound.ENTITY_COW_MILK),
                    () -> {
                        player.sendMessage("§c挤奶失败！牛生气了！");
                        cow.damage(1.0);
                        plugin.getStressManager().addTemporaryStress(cow, 20.0);
                    }));
        }
        // Bucket handling (Vanilla) - check exhaustion
        else if (item.getType() == Material.BUCKET) {
            if (plugin.getExhaustionManager().isExhausted(cow)) {
                plugin.getStressManager().addTemporaryStress(cow, 20.0);
                plugin.getExhaustionManager().playExhaustionEffect(cow); // Visual feedback
                player.sendMessage("§c它很累，产奶量可能受影响。(应激值上升)");
            }
        }
    }

    @EventHandler
    public void onSheepInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (!(event.getRightClicked() instanceof Sheep))
            return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (now - interactionCooldowns.getOrDefault(player.getUniqueId(), 0L) < 200)
            return;
        interactionCooldowns.put(player.getUniqueId(), now);

        Sheep sheep = (Sheep) event.getRightClicked();

        // Prevent QTE conflict
        if (plugin.getQTEManager().isQTEActive(player)) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        // Check for Custom Shears (Now IRON_HOE)
        boolean isPastureShears = false;
        if (item.getType() == Material.IRON_HOE && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == 30003) {
            isPastureShears = true;
        }

        if (isPastureShears) {
            // Check Exhaustion
            if (plugin.getExhaustionManager().isExhausted(sheep)) {
                double penalty = plugin.getConfig().getDouble("stress.interaction_penalty", 20.0);
                plugin.getStressManager().addTemporaryStress(sheep, penalty);
                plugin.getExhaustionManager().playExhaustionEffect(sheep); // Visual feedback
                player.sendMessage("§c这就去剪毛吗？它看起来很累。(应激值上升)");
            }

            String specialtyItemKey = getSpecialtyDrop(sheep);
            EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(sheep);

            if (specialtyItemKey != null && load.isValid && !plugin.getExhaustionManager().isExhausted(sheep)) {
                // Potential Specialty Harvest
                event.setCancelled(true);

                // Start QTE
                plugin.getQTEManager().startQTE(player, new SheepShearingQTE(plugin, player, sheep,
                        () -> {
                            doSpecialtyHarvest(player, sheep, item, specialtyItemKey, Sound.ENTITY_SHEEP_SHEAR);
                            shearSheep(sheep, true);
                        },
                        () -> {
                            player.sendMessage("§c剪坏了！");
                            plugin.getStressManager().addTemporaryStress(sheep, 10.0);
                        }));
            } else {
                // Fallback or handle normal shears logic if needed, but since it's an Iron Hoe,
                // do nothing if not valid for QTE
            }
        }
    }

    @EventHandler
    public void onShear(PlayerShearEntityEvent event) {
        if (!(event.getEntity() instanceof Sheep))
            return;
        // This handles vanilla shears.
        // Since Custom Shears are now Iron Hoe, this event won't fire for them.
        // We leave this for vanilla shears compatibility if desired, or remove if
        // vanilla shears should be disabled?
        // User didn't ask to disable vanilla shears, only to fix the custom one.
        // But we added QTE protection here before, let's keep it.

        Player player = event.getPlayer();

        // Prevent QTE conflict
        if (plugin.getQTEManager().isQTEActive(player)) {
            event.setCancelled(true);
            return;
        }
    }

    private void shearSheep(Sheep sheep, boolean sheared) {
        // Use reflection to bypass compile-time deprecation warnings and "unnecessary
        // suppression" conflicts
        try {
            java.lang.reflect.Method method = sheep.getClass().getMethod("setSheared", boolean.class);
            method.invoke(sheep, sheared);
        } catch (Exception e) {
            // If the method is removed in future versions, we can log it or fallback
            // plugin.getLogger().warning("Failed to set sheared state: " + e.getMessage());
        }
    }

    @EventHandler
    public void onChickenInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Chicken))
            return;
        Chicken chicken = (Chicken) event.getRightClicked();
        Player player = event.getPlayer();

        // Prevent QTE conflict
        if (plugin.getQTEManager().isQTEActive(player)) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.BRUSH) {
            EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(chicken);
            String specialty = getSpecialtyDrop(chicken);

            if (specialty != null && load.isValid && !plugin.getExhaustionManager().isExhausted(chicken)) {
                event.setCancelled(true);
                plugin.getQTEManager().startQTE(player, new ChickenBrushingQTE(plugin, player, chicken,
                        () -> doSpecialtyHarvest(player, chicken, item, specialty, Sound.ENTITY_CHICKEN_HURT),
                        () -> {
                            player.sendMessage("§c鸡被吓到了！");
                            plugin.getStressManager().addTemporaryStress(chicken, 15.0);
                        }));
            } else {
                handleSpecialtyHarvest(event, chicken, item, "特产羽毛", Sound.ENTITY_CHICKEN_HURT);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPigInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (!(event.getRightClicked() instanceof Pig))
            return;
        Pig pig = (Pig) event.getRightClicked();
        Player player = event.getPlayer();

        // Prevent QTE conflict
        if (plugin.getQTEManager().isQTEActive(player)) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.GOLDEN_SHOVEL) {
            EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(pig);
            String specialty = getSpecialtyDrop(pig);
            if (specialty != null && load.isValid && !plugin.getExhaustionManager().isExhausted(pig)) {
                event.setCancelled(true);
                plugin.getQTEManager().startQTE(player, new PigTruffleQTE(plugin, player, pig,
                        () -> {
                            // onDig
                            dropSpecialtyItem(player, pig, specialty, Sound.ITEM_SHOVEL_FLATTEN);
                        },
                        () -> {
                            // onSuccess (Complete)
                            plugin.getExhaustionManager().setExhausted(pig, 15 * 60 * 1000L);
                            if (item != null)
                                item.damage(1, player);
                            player.sendMessage("§a[牧野之歌] 搜寻结束！");
                        },
                        () -> {
                            // onFailure
                            plugin.getStressManager().addTemporaryStress(pig, 5.0);
                        }));
            } else {
                handleSpecialtyHarvest(event, pig, item, "特产松露", Sound.ITEM_SHOVEL_FLATTEN);
            }
        }
    }

    private void handleSpecialtyHarvest(PlayerInteractEntityEvent event, LivingEntity entity, ItemStack tool,
            String name, Sound sound) {
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
            plugin.getExhaustionManager().playExhaustionEffect(entity); // Visual feedback
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
        // If we reach here via direct interaction (not QTE), we perform the harvest
        doSpecialtyHarvest(player, entity, tool, specialtyKey, sound);
    }

    private void doSpecialtyHarvest(Player player, LivingEntity entity, ItemStack tool, String specialtyKey,
            Sound sound) {
        dropSpecialtyItem(player, entity, specialtyKey, sound);

        plugin.getExhaustionManager().setExhausted(entity, 15 * 60 * 1000L);

        if (tool != null) {
            tool.damage(1, player);
        }
    }

    private void dropSpecialtyItem(Player player, LivingEntity entity, String specialtyKey, Sound sound) {
        player.sendMessage("§a[牧野之歌] 成功获取特产！");
        entity.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, entity.getLocation().add(0, 1, 0), 10,
                0.5, 0.5, 0.5, 0.1); // Success particles

        double multiplier = getSpecialtyGeneMultiplier(entity);
        int amount = (int) multiplier;
        if (ThreadLocalRandom.current().nextDouble() < (multiplier - amount)) {
            amount++;
        }
        if (amount < 1)
            amount = 1;

        ItemStack result = getSpecialtyItemStack(specialtyKey);
        if (result != null) {
            result.setAmount(amount);
            applyQualityLore(result, getQualityStars(entity));
            entity.getWorld().dropItemNaturally(entity.getLocation(), result);
        } else {
            player.sendMessage("§cError: Item not found (" + specialtyKey + ")");
        }

        player.playSound(entity.getLocation(), sound, 1.0f, 1.0f);
    }

    // --- Helpers ---

    private static final List<String> BASIC_KEYS = List.of("RICH_MILK", "SOFT_WOOL", "FINE_FEATHER", "WHITE_TRUFFLE");

    private String getSpecialtyDrop(LivingEntity entity) {
        String entityType = entity.getType().name();

        try {
            // Check if BiomeGifts is available
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("BiomeGifts") == null) {
                throw new NoClassDefFoundError("BiomeGifts plugin not found");
            }

            List<ResourceConfig> configs = BiomeGifts.getInstance().getConfigManager().getLivestockConfigs(entityType);

            if (configs == null || configs.isEmpty())
                return getDefaultSpecialty(entityType);

            String biomeName = entity.getLocation().getBlock().getBiome().name();
            List<String> matches = new ArrayList<>();

            for (ResourceConfig rc : configs) {
                for (Pattern pattern : rc.richBiomes) {
                    if (pattern.matcher(biomeName).find()) {
                        matches.add(rc.dropItem);
                        break; // Matched this config, move to next config
                    }
                }
            }

            if (matches.isEmpty()) {
                return getDefaultSpecialty(entityType);
            }

            // Identify Basic vs Special
            String special = null;
            String basic = null;

            for (String key : matches) {
                if (BASIC_KEYS.contains(key)) {
                    basic = key;
                } else {
                    special = key; // Assume any non-basic is special
                }
            }

            if (special != null) {
                if (basic != null) {
                    // Both available (In Special Biome)
                    if (ThreadLocalRandom.current().nextDouble() < 0.4) {
                        return special;
                    } else {
                        return basic;
                    }
                } else {
                    return special;
                }
            }

            return basic; // Only Basic or null

        } catch (Throwable t) {
            // Fallback if BiomeGifts is missing or errors out
            return getDefaultSpecialty(entityType);
        }
    }

    private String getDefaultSpecialty(String entityType) {
        if (entityType.equals("COW"))
            return "RICH_MILK";
        if (entityType.equals("SHEEP"))
            return "SOFT_WOOL";
        if (entityType.equals("CHICKEN"))
            return "FINE_FEATHER";
        if (entityType.equals("PIG"))
            return "WHITE_TRUFFLE";
        return null;
    }

    private double getSpecialtyGeneMultiplier(LivingEntity entity) {
        if (!(entity instanceof Animals))
            return 1.0;
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(entity);
        if (genes == null)
            return 1.0;
        double f = genes.getGenePair(Trait.FERTILITY).getPhenotypeValue();
        // Formula: 1.0 + (Score / 10.0)
        // Score 14 -> 2.4x
        return 1.0 + (f / 10.0);
    }

    private int getQualityStars(LivingEntity entity) {
        if (!(entity instanceof Animals))
            return 1;
        Animals animal = (Animals) entity;
        GeneData genes = plugin.getGeneticsManager().getGenesFromEntity(entity);
        if (genes == null)
            return 1;

        // Use the new centralized method for Drop Quality Rating
        int stars = plugin.getGeneticsManager().calculateProductionRating(genes);

        // Stress Penalty
        double stress = plugin.getStressManager().getTotalStress(animal);
        if (stress > 99)
            stars = 1;
        else {
            if (stress > 70)
                stars -= 2;
            else if (stress > 30)
                stars -= 1;
        }

        if (stars < 1)
            stars = 1;
        return stars;
    }

    private ItemStack getSpecialtyItemStack(String key) {
        try {
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("BiomeGifts") != null) {
                return BiomeGifts.getInstance().getItemManager().getItem(key);
            }
        } catch (Throwable ignored) {
        }

        // Fallback Item Creation
        Material mat = Material.PAPER;
        String name = "未知特产";
        int model = 0;

        switch (key) {
            case "RICH_MILK":
                mat = Material.MILK_BUCKET; // Or generic item
                name = "§6醇香牛奶";
                break;
            case "SOFT_WOOL":
                mat = Material.WHITE_WOOL;
                name = "§6柔软羊毛";
                break;
            case "FINE_FEATHER":
                mat = Material.FEATHER;
                name = "§6优质羽毛";
                break;
            case "WHITE_TRUFFLE":
                mat = Material.BROWN_MUSHROOM;
                name = "§6白松露";
                model = 1001;
                break;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            if (model > 0)
                meta.setCustomModelData(model);

            // Mark as specialty for detection
            NamespacedKey biomeGiftKey = NamespacedKey.fromString("biomegifts:id");
            if (biomeGiftKey != null) {
                meta.getPersistentDataContainer().set(biomeGiftKey, PersistentDataType.STRING, key);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyQualityLore(ItemStack item, int stars) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        // Check Specialty
        boolean isSpecialty = false;
        Component name = meta.displayName();
        if (name == null)
            name = Component.translatable(item.getType().translationKey());

        String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(name);
        if (plainName.contains("醇香牛奶") || plainName.contains("醇香牛乳") ||
                plainName.contains("柔软羊毛") || plainName.contains("优质羽毛") ||
                plainName.contains("白松露") || plainName.contains("黑松露")) {
            isSpecialty = true;
        }

        NamespacedKey biomeGiftKey = NamespacedKey.fromString("biomegifts:id");
        if (biomeGiftKey != null && meta.getPersistentDataContainer().has(biomeGiftKey, PersistentDataType.STRING)) {
            isSpecialty = true;
        }

        // Use NexusCore API
        com.nexuscore.NexusCore.getInstance().getTierVisuals().applyVisuals(item, stars, !isSpecialty);

        // Update meta reference as applyVisuals updates it?
        // No, applyVisuals modifies the itemstack's meta.
        // But we need to set PDC on the meta.
        // applyVisuals calls item.setItemMeta(meta) at the end.
        // So item has the new meta. We should re-get it or just use item.getItemMeta().
        meta = item.getItemMeta();

        // Write star rating to PDC
        NamespacedKey starKey = NamespacedKey.fromString("cuisinefarming:star_rating");
        if (starKey != null) {
            meta.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, stars);
        }

        item.setItemMeta(meta);
    }

}
