package com.example.pasturesong.environment;

import com.example.pasturesong.PastureSong;
import com.example.pasturesong.integration.TownySupport;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class EnvironmentManager {
    private final PastureSong plugin;
    private static final int MAX_SEARCH_SIZE = 500;
    
    // Configurable Values
    // private double capacityLimit; // Deprecated by Power Law formula
    // private double capacityK; // Deprecated by Power Law formula
    private double loadManure;
    private double loadDefault;
    private Map<String, Double> animalLoads;

    private static final Set<Material> DIRT_LIKE_SET = new HashSet<>();
    private final Map<String, EnclosureData> enclosureCache = new HashMap<>();
    private final List<BrokenPasture> brokenPastures = new ArrayList<>();
    
    private static class BrokenPasture {
        BoundingBox box;
        Location center;
        long timestamp;
        
        BrokenPasture(BoundingBox box, Location center) {
            this.box = box;
            this.center = center;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
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
        
        // this.capacityLimit = config.getDouble("environment.capacity.limit", 50.0);
        // this.capacityK = config.getDouble("environment.capacity.k_value", 20.0);
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
    
    public void handleBlockChange(Block block, Material oldType, Material newType) {
        String key = getBlockKey(block);
        
        // 1. Check for Manure/Grass Swap (Optimization: Don't invalidate, just update)
        boolean isGrassToPodzol = (oldType == Material.GRASS_BLOCK && newType == Material.PODZOL);
        boolean isPodzolToGrass = (oldType == Material.PODZOL && newType == Material.GRASS_BLOCK);
        
        if (enclosureCache.containsKey(key)) {
            EnclosureData data = enclosureCache.get(key);
            if (isGrassToPodzol) {
                data.grassCount--;
                data.manureCount++;
                return;
            }
            if (isPodzolToGrass) {
                data.grassCount++;
                data.manureCount--;
                return;
            }
        }
        
        // 2. Structural or Floor Change -> Invalidate
        // Check if this block was part of an enclosure
        invalidateCache(block.getLocation());
        
        // 3. Check Neighbors (in case we placed a wall that splits an enclosure)
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        for (BlockFace face : faces) {
            invalidateCache(block.getRelative(face).getLocation());
        }
    }
    
    public void invalidateCache(Location loc) {
        String key = getBlockKey(loc.getBlock());
        if (enclosureCache.containsKey(key)) {
            EnclosureData data = enclosureCache.get(key);
            
            // If it was a valid pasture, record it as potentially broken
            if (data.isValid && data.memberBlocks != null) {
                BoundingBox box = new BoundingBox(
                    data.minX, data.minY, data.minZ,
                    data.maxX + 1, data.maxY + 1, data.maxZ + 1
                );
                Location center = new Location(loc.getWorld(), (data.minX + data.maxX) / 2.0, (data.minY + data.maxY) / 2.0, (data.minZ + data.maxZ) / 2.0);
                brokenPastures.add(new BrokenPasture(box, center));
                
                // Notify vaguely about structure change
                // TownySupport.notifyOwner(center, "§e[牧场] 牧场结构发生变动...");
            }

            if (data.memberBlocks != null) {
                for (String memberKey : data.memberBlocks) {
                    enclosureCache.remove(memberKey);
                }
            } else {
                enclosureCache.remove(key);
            }
        }
    }

    /**
     * Calculates the load status for an entity in its current enclosure.
     */
    public LoadResult getLoad(LivingEntity entity) {
        Location loc = entity.getLocation();
        String key = getBlockKey(loc.getBlock());
        
        EnclosureData data;
        
        // Clean up old broken pastures (older than 1 minute)
        long now = System.currentTimeMillis();
        brokenPastures.removeIf(bp -> now - bp.timestamp > 60000);

        // Check Cache
        if (enclosureCache.containsKey(key)) {
            data = enclosureCache.get(key);
        } else {
            // Scan
            data = scanEnclosure(loc);
            
            // Check for Status Changes (Activation/Deactivation)
            if (data.isValid) {
                // Check if this location was in a broken pasture list (Restored/Modified)
                Iterator<BrokenPasture> it = brokenPastures.iterator();
                while (it.hasNext()) {
                    BrokenPasture bp = it.next();
                    if (bp.box.contains(loc.toVector())) {
                        TownySupport.notifyOwner(loc, "§a[牧场] 牧场结构更新，当前状态：有效。");
                        it.remove();
                        break;
                    }
                }
            } else {
                // Check if this WAS a valid pasture
                 Iterator<BrokenPasture> it = brokenPastures.iterator();
                while (it.hasNext()) {
                    BrokenPasture bp = it.next();
                    if (bp.box.contains(loc.toVector())) {
                        TownySupport.notifyOwner(bp.center, "§c[牧场] 警告：牧场结构失效！");
                        it.remove();
                        break;
                    }
                }
            }

            // Update Cache
            if (data.isValid && data.memberBlocks != null) {
                for (String blockKey : data.memberBlocks) {
                    enclosureCache.put(blockKey, data);
                }
            } else {
                enclosureCache.put(key, data);
            }
        }
        
        // Calculate Dynamic Load (Entities)
        LoadResult result;
        if (!data.isValid) {
            // Invalid enclosure
            if (data.isOpenWorld) {
                // Nature World: No limits, no overload, treated as valid for vanilla gameplay but invalid for pasture stats
                // We return isValid=false to indicate "Not a Pasture", but we ensure no penalties are applied.
                // Capacity = Infinite, Load = 0 (or actual, but irrelevant), Overloaded = false
                result = new LoadResult(999999.0, 0.0, false, 0.0, false, true);
            } else {
                // Invalid floor: Penalty
                result = new LoadResult(0.0, 10.0, true, 10.0, false, false);
            }
        } else {
            double capacity = calculateCapacity(data.area);
            double currentLoad = calculateEntityLoad(data, entity.getWorld());
            currentLoad += (data.manureCount * loadManure); // Add Manure Load
            boolean overloaded = currentLoad > capacity;
            
            // Check Overload Notification
            Location center = new Location(loc.getWorld(), (data.minX + data.maxX) / 2.0, (data.minY + data.maxY) / 2.0, (data.minZ + data.maxZ) / 2.0);
            if (overloaded && !data.notifiedOverload) {
                TownySupport.notifyOwner(center, String.format("§c[牧场] 警告：牧场处于过载状态！(负载: %.1f / %.1f)", currentLoad, capacity));
                data.notifiedOverload = true;
            } else if (!overloaded && data.notifiedOverload) {
                TownySupport.notifyOwner(center, "§a[牧场] 牧场负载已恢复正常。");
                data.notifiedOverload = false;
            }

            double ratio = (capacity > 0) ? (currentLoad / capacity) : 0.0;
            result = new LoadResult(capacity, currentLoad, overloaded, ratio, true);
        }
        
        return result;
    }

    private double calculateCapacity(int area) {
        // Old Formula: Limit * (Area / (Area + K)) -> Saturation curve (Bad for large pastures)
        // New Formula: Power Law -> 0.8 * Area^0.8
        // Area 20 -> ~8.8
        // Area 100 -> ~32
        // Area 500 -> ~115
        // This ensures small pastures aren't overcrowded, and large pastures are rewarding.
        return 0.8 * Math.pow(area, 0.8);
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
                // Do NOT break here. We need to continue scanning to see if it's Open World.
                // If we break, we assume it's a small enclosed area with bad floor.
                // By continuing, if we hit MAX_SEARCH_SIZE, it becomes Open World (Nature).
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
        
        public boolean notifiedOverload = false;
        
        public EnclosureData(int area, int grassCount, boolean isOpenWorld) {
            this.area = area;
            this.grassCount = grassCount;
            this.isOpenWorld = isOpenWorld;
            this.isValid = !isOpenWorld && area > 0; // Require area > 0 for validity
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
        public boolean isOpenWorld;
        
        public LoadResult(double capacity, double currentLoad, boolean overloaded, double overloadRatio, boolean isValid) {
            this(capacity, currentLoad, overloaded, overloadRatio, isValid, false);
        }

        public LoadResult(double capacity, double currentLoad, boolean overloaded, double overloadRatio, boolean isValid, boolean isOpenWorld) {
            this.capacity = capacity;
            this.currentLoad = currentLoad;
            this.overloaded = overloaded;
            this.overloadRatio = overloadRatio;
            this.isValid = isValid;
            this.isOpenWorld = isOpenWorld;
        }
    }
}
