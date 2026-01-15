package com.example.pasturesong;

import com.example.pasturesong.environment.EnvironmentListener;
import com.example.pasturesong.environment.EnvironmentManager;
import com.example.pasturesong.environment.ManureListener;
import com.example.pasturesong.environment.ManureManager;
import com.example.pasturesong.genetics.GeneticsManager;
import com.example.pasturesong.stress.StressListener;
import com.example.pasturesong.stress.StressManager;
import com.example.pasturesong.listeners.LifecycleListener;
import com.example.pasturesong.listeners.ToolListener;
import com.example.pasturesong.production.ExhaustionManager;
import com.example.pasturesong.production.ProductionListener;
import com.example.pasturesong.tasks.PastureTask;
import com.example.pasturesong.commands.PastureCommand;
import com.example.pasturesong.disease.DiseaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PastureSong extends JavaPlugin {
    private static PastureSong instance;
    private GeneticsManager geneticsManager;
    private StressManager stressManager;
    private ManureManager manureManager;
    private EnvironmentManager environmentManager;
    private ExhaustionManager exhaustionManager;
    private PastureItemManager itemManager;
    private DiseaseManager diseaseManager;
    private com.example.pasturesong.qte.QTEManager qteManager;
    private com.example.pasturesong.tasks.LensTask lensTask;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize Managers
        this.geneticsManager = new GeneticsManager(this);
        this.stressManager = new StressManager(this);
        this.manureManager = new ManureManager(this);
        this.environmentManager = new EnvironmentManager(this);
        this.exhaustionManager = new ExhaustionManager(this);
        this.itemManager = new PastureItemManager(this);
        this.diseaseManager = new DiseaseManager(this);
        this.qteManager = new com.example.pasturesong.qte.QTEManager(this);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new StressListener(this), this);
        getServer().getPluginManager().registerEvents(new ManureListener(this), this);
        getServer().getPluginManager().registerEvents(new LifecycleListener(this), this);
        getServer().getPluginManager().registerEvents(new ProductionListener(this), this);
        getServer().getPluginManager().registerEvents(new EnvironmentListener(this), this);
        getServer().getPluginManager().registerEvents(new ToolListener(this), this);
        getServer().getPluginManager().registerEvents(new com.example.pasturesong.listeners.CombatListener(this), this);

        // Start Tasks
        // Run every 100 ticks (5 seconds)
        new PastureTask(this).runTaskTimer(this, 100L, 100L);
        // Run LensTask every 5 ticks (0.25 seconds)
        this.lensTask = new com.example.pasturesong.tasks.LensTask(this);
        this.lensTask.runTaskTimer(this, 5L, 5L);

        // Register Commands
        if (getCommand("ps") != null) {
            getCommand("ps").setExecutor(new PastureCommand(this));
        }

        // NexusCore Integration and Listener
        registerWithNexusCore();
        
        // Save nexus_recipes.yml if not exists
        if (!new java.io.File(getDataFolder(), "nexus_recipes.yml").exists()) {
            saveResource("nexus_recipes.yml", false);
        }

        // Load Nexus Recipes
        try {
            com.nexuscore.recipe.RecipeConfigLoader.load(com.nexuscore.NexusCore.getInstance(), new java.io.File(getDataFolder(), "nexus_recipes.yml"));
            getLogger().info("Loaded Nexus recipes.");
        } catch (Exception e) {
            getLogger().warning("Failed to load Nexus recipes: " + e.getMessage());
        }

        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
                if (event.getPlugin().getName().equals("NexusCore")) {
                    getLogger().info("NexusCore re-enabled detected. Re-registering modules...");
                    registerWithNexusCore();
                }
            }
        }, this);

        getLogger().info("PastureSong has been enabled! 牧野之歌，奏响序曲。");
    }

    @Override
    public void onDisable() {
        getLogger().info("PastureSong has been disabled.");
    }

    public static PastureSong getInstance() {
        return instance;
    }

    public GeneticsManager getGeneticsManager() {
        return geneticsManager;
    }

    public StressManager getStressManager() {
        return stressManager;
    }

    public ManureManager getManureManager() {
        return manureManager;
    }

    public EnvironmentManager getEnvironmentManager() {
        return environmentManager;
    }

    public ExhaustionManager getExhaustionManager() {
        return exhaustionManager;
    }

    public PastureItemManager getItemManager() {
        return itemManager;
    }

    public DiseaseManager getDiseaseManager() {
        return diseaseManager;
    }

    public com.example.pasturesong.qte.QTEManager getQTEManager() {
        return qteManager;
    }

    public com.example.pasturesong.tasks.LensTask getLensTask() {
        return lensTask;
    }

    public void registerWithNexusCore() {
        org.bukkit.plugin.Plugin nexusCore = getServer().getPluginManager().getPlugin("NexusCore");
        if (nexusCore != null && nexusCore.isEnabled()) {
            try {
                Object registry = nexusCore.getClass().getMethod("getRegistry").invoke(nexusCore);
                
                java.lang.reflect.Method registerMethod = null;
                for (java.lang.reflect.Method m : registry.getClass().getMethods()) {
                    if (m.getName().equals("register") && m.getParameterCount() == 6) {
                        registerMethod = m;
                        break;
                    }
                }
                
                if (registerMethod == null) {
                    registerMethod = registry.getClass().getMethod("register", 
                        String.class, String.class, java.util.function.Supplier.class, java.util.function.Supplier.class);
                }

                java.util.function.Supplier<org.bukkit.inventory.ItemStack> iconSupplier = () -> new org.bukkit.inventory.ItemStack(org.bukkit.Material.WHEAT);
                java.util.function.Supplier<java.util.List<org.bukkit.inventory.ItemStack>> itemsSupplier = () -> new java.util.ArrayList<>(itemManager.getAllItems());

                // Star Filter: Non-blocks only
                java.util.function.Function<org.bukkit.inventory.ItemStack, Boolean> starFilter = (item) -> {
                    // All PastureSong items currently have NO STAR
                    return false;
                };

                if (registerMethod.getParameterCount() == 6) {
                    registerMethod.invoke(registry, "pasture-song", "PastureSong", iconSupplier, itemsSupplier, null, starFilter);
                } else {
                    registerMethod.invoke(registry, "pasture-song", "PastureSong", iconSupplier, itemsSupplier);
                }

                getLogger().info("Registered items with NexusCore Nexus (Using Reflection).");
            } catch (Exception e) {
                getLogger().warning("Failed to register with NexusCore: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}