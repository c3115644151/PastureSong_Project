package com.example.pasturesong.commands;

import com.example.pasturesong.PastureSong;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
            default:
                sendHelp(sender);
                break;
        }

        return true;
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
        } else if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            completions.add("GENETIC_LENS");
            completions.add("DNA_SAMPLER");
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
