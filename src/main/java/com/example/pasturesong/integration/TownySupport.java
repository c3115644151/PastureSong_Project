package com.example.pasturesong.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class TownySupport {

    private static boolean enabled = false;

    static {
        try {
            Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            enabled = true;
        } catch (ClassNotFoundException e) {
            enabled = false;
        }
    }

    public static void notifyOwner(Location location, String message) {
        if (!enabled) return;

        try {
            Town town = TownyAPI.getInstance().getTown(location);
            if (town != null) {
                Resident mayor = town.getMayor();
                if (mayor != null) {
                    Player player = Bukkit.getPlayer(mayor.getName());
                    if (player != null && player.isOnline()) {
                        player.sendMessage(message);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }
}
