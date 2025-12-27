package com.example.pasturesong.commands;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.enchantments.ButcheryEnchantment;
import com.example.pasturesong.genetics.Allele;
import com.example.pasturesong.genetics.GeneData;
import com.example.pasturesong.genetics.GenePair;
import com.example.pasturesong.genetics.Trait;
import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PastureCommand implements CommandExecutor, TabCompleter {

    private final PastureSong plugin;

    public PastureCommand(PastureSong plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("pasturesong.admin")) {
            sender.sendMessage(Component.text("§cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "get":
                handleGet(sender, args);
                break;
            case "debug":
                handleDebug(sender, args);
                break;
            case "verify":
                handleVerify(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return;
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage("§cUsage: /ps debug <stress|infect|cure|exhaust|info>");
            return;
        }
        
        org.bukkit.entity.Entity target = getTargetEntity(player);
        if (!(target instanceof org.bukkit.entity.LivingEntity)) {
             player.sendMessage("§c请看着一个生物 (Look at a mob)!");
             return;
        }
        // Check livestock manually here or expose the method publicly
        // Since isLivestock is private in ToolListener, let's just duplicate the check or use a simpler check for now.
        // Actually, let's make isLivestock public static in ToolListener is the best way, but user asked to fix errors.
        // Error: "The method isLivestock... is not visible"
        // So I will change ToolListener to make it public static.
        
        if (!com.example.pasturesong.listeners.ToolListener.isLivestock((org.bukkit.entity.LivingEntity)target)) {
            player.sendMessage("§c请看着一个家畜 (Look at a livestock)!");
            return;
        }
        org.bukkit.entity.Animals animal = (org.bukkit.entity.Animals) target;
        
        String action = args[1].toLowerCase();
        switch (action) {
            case "stress":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /ps debug stress <amount>");
                    return;
                }
                double amount = Double.parseDouble(args[2]);
                plugin.getStressManager().setTemporaryStress(animal, amount);
                player.sendMessage("§aSet stress to " + amount);
                break;
            case "infect":
                plugin.getDiseaseManager().setSick(animal, true);
                player.sendMessage("§cAnimal infected!");
                break;
            case "cure":
                plugin.getDiseaseManager().setSick(animal, false);
                player.sendMessage("§aAnimal cured!");
                break;
            case "exhaust":
                plugin.getExhaustionManager().setExhausted(animal, 15 * 60 * 1000L);
                player.sendMessage("§cAnimal exhausted!");
                break;
            case "recover":
                plugin.getExhaustionManager().setExhausted(animal, 0);
                player.sendMessage("§aAnimal recovered from exhaustion!");
                break;
            case "info":
                double stress = plugin.getStressManager().getTotalStress(animal);
                boolean sick = plugin.getDiseaseManager().isSick(animal);
                boolean exhausted = plugin.getExhaustionManager().isExhausted(animal);
                com.example.pasturesong.environment.EnvironmentManager.LoadResult load = plugin.getEnvironmentManager().getLoad(animal);
                
                player.sendMessage("§6=== Debug Info ===");
                player.sendMessage("§7Stress: §f" + String.format("%.1f", stress));
                player.sendMessage("§7Sick: " + (sick ? "§cYES" : "§aNO"));
                player.sendMessage("§7Exhausted: " + (exhausted ? "§cYES" : "§aNO"));
                player.sendMessage("§7Enclosure: " + (load.isValid ? "§aValid" : "§cInvalid"));
                player.sendMessage("§7Load: §f" + String.format("%.1f", load.currentLoad) + " / " + String.format("%.1f", load.capacity));
                break;
        }
    }
    
    private void handleVerify(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            player.sendMessage("§cUsage: /ps verify <health|butcher|damage>");
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "health":
                // /ps verify health <type> <stars>
                if (args.length < 4) {
                    player.sendMessage("§cUsage: /ps verify health <cow|pig|sheep|chicken> <1-5>");
                    return;
                }
                String typeStr = args[2].toUpperCase();
                int stars;
                try {
                    stars = Integer.parseInt(args[3]);
                    if (stars < 1) stars = 1;
                    if (stars > 5) stars = 5;
                } catch (NumberFormatException e) {
                    player.sendMessage("§cStars must be a number 1-5.");
                    return;
                }
                
                EntityType type;
                try {
                    type = EntityType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cInvalid entity type: " + typeStr);
                    return;
                }
                
                // Spawn entity
                LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), type);
                
                // Construct genes for target stars
                GeneData genes = new GeneData();
                
                // Simple mapping for stars:
                // 1 Star: RECESSIVE_4 (-4) -> very low score
                // 3 Star: DOMINANT_1 (1) -> medium
                // 5 Star: DOMINANT_4 (4) -> high
                
                if (stars == 5) {
                    for (Trait t : Trait.values()) {
                        genes.setGenePair(t, new GenePair(Allele.DOMINANT_4, Allele.DOMINANT_4));
                    }
                } else if (stars == 4) {
                    for (Trait t : Trait.values()) {
                        genes.setGenePair(t, new GenePair(Allele.DOMINANT_3, Allele.DOMINANT_3));
                    }
                } else if (stars == 3) {
                    for (Trait t : Trait.values()) {
                        genes.setGenePair(t, new GenePair(Allele.DOMINANT_1, Allele.DOMINANT_1));
                    }
                } else if (stars == 2) {
                    // Avg need [0, 2). Mix DOMINANT_1 (+2) and RECESSIVE_1 (-2) to get 0.
                    Trait[] traits = Trait.values();
                    for (int i = 0; i < traits.length; i++) {
                        if (i % 2 == 0) {
                            genes.setGenePair(traits[i], new GenePair(Allele.DOMINANT_1, Allele.DOMINANT_1));
                        } else {
                            genes.setGenePair(traits[i], new GenePair(Allele.RECESSIVE_1, Allele.RECESSIVE_1));
                        }
                    }
                } else {
                    // 1 Star
                    for (Trait t : Trait.values()) {
                        genes.setGenePair(t, new GenePair(Allele.RECESSIVE_4, Allele.RECESSIVE_4));
                    }
                }
                
                // Apply genes
                plugin.getGeneticsManager().applyGeneAttributes(entity, genes);
                
                // Check result
                int calculatedStars = plugin.getGeneticsManager().calculateProductionRating(genes);
                double health = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                
                player.sendMessage("§aSpawned " + typeStr + " with " + calculatedStars + " stars (Requested: " + stars + ")");
                player.sendMessage("§aMax Health: " + health + " (" + (health/2.0) + " Hearts)");
                break;
                
            case "butcher":
                // /ps verify butcher <level>
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /ps verify butcher <level>");
                    return;
                }
                int level;
                try {
                    level = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cLevel must be a number.");
                    return;
                }
                
                ItemStack sword = plugin.getItemManager().getItem("BUTCHER_KNIFE");
                if (sword != null) {
                    ButcheryEnchantment.apply(sword, level, plugin);
                    player.getInventory().addItem(sword);
                    player.sendMessage("§aGiven Butcher Knife Level " + level);
                } else {
                    player.sendMessage("§cError: Butcher Knife item not found.");
                }
                break;
                
            case "damage":
                // /ps verify damage
                org.bukkit.entity.Entity target = getTargetEntity(player);
                if (!(target instanceof LivingEntity)) {
                    player.sendMessage("§cLook at a living entity.");
                    return;
                }
                if (!(target instanceof org.bukkit.entity.Animals)) {
                    player.sendMessage("§cTarget is not an animal.");
                    return;
                }
                
                ItemStack hand = player.getInventory().getItemInMainHand();
                int bLevel = ButcheryEnchantment.getLevel(hand, plugin);
                double baseDmg = 7.0; // Diamond Sword default
                // If not diamond sword, get actual damage? Bukkit doesn't expose base damage easily without NMS or attribute check.
                // We'll assume Diamond Sword for verification.
                
                double bonus = 0;
                if (bLevel > 0) {
                     // Logic from ProductionListener
                     double baseBonus = 14.0; 
                     double levelBonus = bLevel * 10.0;
                     bonus = baseBonus + levelBonus;
                }
                
                double total = baseDmg + bonus;
                player.sendMessage("§6=== Damage Verification ===");
                player.sendMessage("§7Weapon: " + hand.getType());
                player.sendMessage("§7Butchery Level: " + bLevel);
                player.sendMessage("§7Base Damage: " + baseDmg);
                player.sendMessage("§7Bonus Damage: " + bonus);
                player.sendMessage("§cTotal Predicted Damage: " + total);
                player.sendMessage("§7Target Health: " + ((LivingEntity)target).getHealth() + " / " + ((LivingEntity)target).getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                break;
        }
    }

    private org.bukkit.entity.Entity getTargetEntity(Player player) {
        return player.getTargetEntity(5);
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("§cThis command can only be used by players."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("§cUsage: /pasturesong get <item>"));
            return;
        }

        String itemName = args[1].toUpperCase();
        ItemStack item = plugin.getItemManager().getItem(itemName);

        if (item == null) {
            sender.sendMessage(Component.text("§cUnknown item: " + itemName));
            return;
        }

        Player player = (Player) sender;
        player.getInventory().addItem(item);
        player.sendMessage(Component.text("§aGiven " + itemName + " to " + player.getName()));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("§6=== PastureSong Commands ==="));
        sender.sendMessage(Component.text("§e/pasturesong get <item> §7- Get a custom item"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("get");
            completions.add("debug");
            completions.add("verify");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            completions.add("GENETIC_LENS");
            completions.add("DNA_SAMPLER");
            completions.add("PASTURE_SHEARS");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            completions.add("stress");
            completions.add("infect");
            completions.add("cure");
            completions.add("exhaust");
            completions.add("recover");
            completions.add("info");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("verify")) {
            completions.add("health");
            completions.add("butcher");
            completions.add("damage");
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
