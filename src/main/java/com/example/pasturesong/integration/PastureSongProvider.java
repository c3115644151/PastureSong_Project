package com.example.pasturesong.integration;

import com.example.pasturesong.PastureSong;
import com.nexuscore.api.NexusItemProvider;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PastureSongProvider implements NexusItemProvider {

    private final PastureSong plugin;

    public PastureSongProvider(PastureSong plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getModuleId() {
        return "pasture-song";
    }

    @Override
    public String getDisplayName() {
        return "PastureSong";
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.WHEAT);
    }

    @Override
    public List<ItemStack> getItems() {
        return new ArrayList<>(plugin.getItemManager().getAllItems());
    }
}
