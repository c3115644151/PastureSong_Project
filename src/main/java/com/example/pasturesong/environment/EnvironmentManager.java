package com.example.pasturesong.environment;

import com.example.pasturesong.PastureSong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class EnvironmentManager {
    private final PastureSong plugin;
    private static final int MAX_SEARCH_SIZE = 500;
    
    // Configurable Values
    private double capacityLimit;
    private double capacityK;
    private double loadManure;
    private double loadDefault;
    private Map<String, Double> animalLoads;

    private static final Set<Material> DIRT_LIKE_SET = new HashSet<>();
    private final Map<String, CachedEnclosure> enclosureCache = new HashMap<>();
    
    static {
        DIRT_LIKE_SET.add(Material.GRASS_BLOCK);
        DIRT_LIKE_SET.add(Material.DIRT);
        DIRT_LIKE_SET.add(Material.COARSE_DIRT);
        DIRT_LIKE_SET.add(Material.PODZOL);
        DIRT_LIKE_SET.add(Material.MYCELIUM);
        // Add others if version supports (e.g. ROOTED_DIRT, MOSS_BLOCK)
        try {
            DIRT_LIKE_SET.add(Material.valueOf("ROOTED_DIRT"));
            DIRT_LIKE_SET.add(Material.valueOf("MOSS_BLOCK"));
            DIRT_LIKE_SET.add(Material.valueOf("MUD"));
        } catch (IllegalArgumentException ignored) {}
    }

    public EnvironmentManager(PastureSong plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        
        this.capacityLimit = config.getDouble("environment.capacity.limit", 50.0);
        this.capacityK = config.getDouble("environment.capacity.k_value", 20.0);
        this.loadManure = config.getDouble("environment.load.manure", 2.0);
        this.loadDefault = config.getDouble("environment.load.default", 1.0);
        
        this.animalLoads = new HashMap<>();
        if (config.contains("environment.load.animals")) {
            for (String key : config.getConfigurationSection("environment.load.animals").getKeys(false)) {
                animalLoads.put(key, config.getDouble("environment.load.animals." + key));
            }
        }
    }

    public void clearCache() {
        enclosureCache.clear();
    }

    /**
     * Calculates the load status for an entity in its current enclosure.
     */
    public LoadResult getLoad(LivingEntity entity) {
        Location loc = entity.getLocation();
        String key = getBlockKey(loc.getBlock());
        
        // Check Cache
        if (enclosureCache.containsKey(key)) {
            return enclosureCache.get(key).loadResult;
        }
        
        // Scan
        EnclosureData data = scanEnclosure(loc);
        
        // Calculate Load
        LoadResult result;
        if (!data.isValid) {
            // Invalid enclosure (Open World or Invalid Floor)
            if (data.isOpenWorld) {
                double capacity = calculateCapacity(data.area);
                double currentLoad = calculateEntityLoad(data, entity.getWorld());
                boolean overloaded = currentLoad > capacity;
                double ratio = (capacity > 0) ? (currentLoad / capacity) : 0.0;
                result = new LoadResult(capacity, currentLoad, overloaded, ratio, false);
            } else {
                // Invalid floor: Penalty
                result = new LoadResult(0.0, 10.0, true, 10.0, false);
            }
        } else {
            double capacity = calculateCapacity(data.area);
            double currentLoad = calculateEntityLoad(data, entity.getWorld());
            currentLoad += (data.manureCount * loadManure);
            boolean overloaded = currentLoad > capacity;
            double ratio = (capacity > 0) ? (currentLoad / capacity) : 0.0;
            result = new LoadResult(capacity, currentLoad, overloaded, ratio, true);
        }
        
        // Update Cache
        if (data.isValid && data.memberBlocks != null) {
            CachedEnclosure cached = new CachedEnclosure(result);
            for (String blockKey : data.memberBlocks) {
                enclosureCache.put(blockKey, cached);
            }
        } else {
            enclosureCache.put(key, new CachedEnclosure(result));
        }
        
        return result;
    }

    private double calculateCapacity(int area) {
        // Formula: Limit * (Area / (Area + K))
        return capacityLimit * ((double) area / (area + capacityK));
    }

    private double calculateEntityLoad(EnclosureData data, org.bukkit.World world) {
        if (data.memberBlocks == null) return 1.0; // Fallback

        // Use Bounding Box to find entities
        org.bukkit.util.BoundingBox box = new org.bukkit.util.BoundingBox(
            data.minX, data.minY, data.minZ,
            data.maxX + 1, data.maxY + 2, data.maxZ + 1
        );
        
        double load = 0.0;
        
        for (Entity entity : world.getNearbyEntities(box)) {
            if (entity instanceof LivingEntity) {
                // Check if the entity is actually inside the enclosure
                String key = getBlockKey(entity.getLocation().getBlock());
                if (data.memberBlocks.contains(key)) {
                    // Assign Load Values from Config
                    String type = entity.getType().name();
                    load += animalLoads.getOrDefault(type, loadDefault);
                }
            }
        }
        
        return load;
    }

    /**
     * Scans the enclosure starting from the given location.
     * Returns an EnclosureData object containing area/grass info.
     * Note: This runs on the main thread and can be heavy. Use asynchronously if possible.
     */
    public EnclosureData scanEnclosure(Location start) {
        Block startBlock = start.getBlock();
        
        // Requirement 1 (Modified): At least one grass block inside (Checked during scan)
        // Requirement 3: Enclosed (Checked by BFS bounds)
        
        if (!isPassable(startBlock)) return new EnclosureData(0, 0, false);

        Set<String> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        
        queue.add(startBlock);
        visited.add(getBlockKey(startBlock));

        int area = 0;
        int grassCount = 0;
        int manureCount = 0; // Need to count manure?
        boolean invalidFloor = false;
        
        int minX = startBlock.getX(), maxX = startBlock.getX();
        int minY = startBlock.getY(), maxY = startBlock.getY();
        int minZ = startBlock.getZ(), maxZ = startBlock.getZ();

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            area++;
            
            // Update Bounds
            if (current.getX() < minX) minX = current.getX();
            if (current.getX() > maxX) maxX = current.getX();
            if (current.getY() < minY) minY = current.getY();
            if (current.getY() > maxY) maxY = current.getY();
            if (current.getZ() < minZ) minZ = current.getZ();
            if (current.getZ() > maxZ) maxZ = current.getZ();
            
            // Check floor type (Block below feet)
            Block floor = current.getRelative(BlockFace.DOWN);
            Material floorType = floor.getType();
            
            // Requirement 2: All floor blocks must be dirt-like
            if (!DIRT_LIKE_SET.contains(floorType)) {
                invalidFloor = true;
                break;
            }

            if (floorType == Material.GRASS_BLOCK) {
                grassCount++;
            }
            
            // Count Manure (Podzol represents manure in this system)
            if (floorType == Material.PODZOL) {
                manureCount++;
            }
            
            if (area >= MAX_SEARCH_SIZE) {
                // Too big, treat as open world
                return new EnclosureData(MAX_SEARCH_SIZE, grassCount, true); 
            }

            // Neighbors (N, S, E, W)
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (BlockFace face : faces) {
                Block neighbor = current.getRelative(face);
                String key = getBlockKey(neighbor);
                
                if (!visited.contains(key) && isPassable(neighbor)) {
                    visited.add(key);
                    queue.add(neighbor);
                }
            }
        }

        if (invalidFloor) {
            return new EnclosureData(0, 0, false); // Invalid enclosure due to non-dirt floor
        }
        
        // Requirement 1 Check: At least one grass block
        if (grassCount == 0) {
            return new EnclosureData(0, 0, false); // Invalid enclosure due to no grass
        }

        EnclosureData data = new EnclosureData(area, grassCount, false);
        data.manureCount = manureCount;
        data.memberBlocks = visited;
        data.minX = minX; data.maxX = maxX;
        data.minY = minY; data.maxY = maxY;
        data.minZ = minZ; data.maxZ = maxZ;
        
        // Calculate Entity Load here?
        // No, do it in getLoad using bounds.
        
        return data;
    }
    
    private String getBlockKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private boolean isPassable(Block block) {
        // Boundary conditions:
        // 1. Fences/Walls are boundaries
        String type = block.getType().toString();
        if (type.contains("FENCE") || type.contains("WALL")) {
            return false;
        }
        
        // 2. Solid blocks at head level are boundaries
        if (block.getType().isSolid()) {
             return false;
        }
        
        return true;
    }
    
    public static class EnclosureData {
        public int area;
        public int grassCount;
        public int manureCount;
        public boolean isOpenWorld;
        public boolean isValid;
        public Set<String> memberBlocks;
        
        // Bounding Box
        public int minX, maxX, minY, maxY, minZ, maxZ;
        
        public EnclosureData(int area, int grassCount, boolean isOpenWorld) {
            this.area = area;
            this.grassCount = grassCount;
            this.isOpenWorld = isOpenWorld;
            this.isValid = !isOpenWorld; // Default
        }
        
        public double getCapacity() {
            if (isOpenWorld) return 9999.0;
            return area * 1.0; 
        }
        
        @Override
        public String toString() {
            return "Area: " + area + ", Grass: " + grassCount + ", Manure: " + manureCount + ", Open: " + isOpenWorld;
        }
    }
    
    public static class LoadResult {
        public double capacity;
        public double currentLoad;
        public boolean overloaded;
        public double overloadRatio;
        public boolean isValid;
        
        public LoadResult(double capacity, double currentLoad, boolean overloaded, double overloadRatio, boolean isValid) {
            this.capacity = capacity;
            this.currentLoad = currentLoad;
            this.overloaded = overloaded;
            this.overloadRatio = overloadRatio;
            this.isValid = isValid;
        }
    }
    
    private static class CachedEnclosure {
        LoadResult loadResult;
        
        public CachedEnclosure(LoadResult loadResult) {
            this.loadResult = loadResult;
        }
    }
}
