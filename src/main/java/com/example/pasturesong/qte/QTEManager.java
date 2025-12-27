package com.example.pasturesong.qte;

import com.example.pasturesong.PastureSong;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QTEManager implements Listener {
    private final Map<UUID, QTE> activeQTEs = new HashMap<>();

    public QTEManager(PastureSong plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void startQTE(Player player, QTE qte) {
        if (activeQTEs.containsKey(player.getUniqueId())) {
            // Already active - reject new QTE request
            return;
        }
        activeQTEs.put(player.getUniqueId(), qte);
        qte.start();
    }

    public void stopQTE(Player player) {
        if (activeQTEs.containsKey(player.getUniqueId())) {
            activeQTEs.get(player.getUniqueId()).stop();
            activeQTEs.remove(player.getUniqueId());
        }
    }

    // Internal method to remove from map without calling stop() recursively
    public void unregisterQTE(Player player) {
        activeQTEs.remove(player.getUniqueId());
    }
    
    public boolean isQTEActive(Player player) {
        return activeQTEs.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (activeQTEs.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            
            // Only handle main hand to prevent double firing
            if (event.getHand() != EquipmentSlot.HAND) return;
            
            QTE qte = activeQTEs.get(player.getUniqueId());
            if (!qte.canAcceptInput()) return;
            
            Action action = event.getAction();
            
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                qte.onLeftClick();
            } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                qte.onRightClick();
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (activeQTEs.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            
            // Only handle main hand
            if (event.getHand() != EquipmentSlot.HAND) return;
            
            QTE qte = activeQTEs.get(player.getUniqueId());
            if (qte.canAcceptInput()) {
                qte.onRightClick();
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (activeQTEs.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            // Handled by onEntityInteract usually, but just in case
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (activeQTEs.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                activeQTEs.get(player.getUniqueId()).onLeftClick();
            }
        }
    }
    
    // Cleanup on Quit
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        stopQTE(event.getPlayer());
    }
}
