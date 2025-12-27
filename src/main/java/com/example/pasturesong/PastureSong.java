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
}