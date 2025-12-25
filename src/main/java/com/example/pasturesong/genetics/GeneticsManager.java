package com.example.pasturesong.genetics;

import com.example.pasturesong.PastureSong;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 基因管理器 (Genetics Manager)
 * 负责基因数据在物品（采样器/蛋）和实体（动物）之间的序列化与传输。
 */
public class GeneticsManager {

    private final NamespacedKey IDENTIFIED_KEY;
    private final Map<Trait, NamespacedKey> TRAIT_KEYS;
    
    public GeneticsManager(PastureSong plugin) {
        this.IDENTIFIED_KEY = new NamespacedKey(plugin, "gene_identified");
        this.TRAIT_KEYS = new java.util.EnumMap<>(Trait.class);
        
        for (Trait trait : Trait.values()) {
            TRAIT_KEYS.put(trait, new NamespacedKey(plugin, "trait_" + trait.name().toLowerCase()));
        }
    }

    // ==========================================
    // 基因生成与遗传逻辑
    // ==========================================

    /**
     * 生成随机基因
     */
    public GeneData generateRandomGenes() {
        GeneData data = new GeneData();
        for (Trait trait : Trait.values()) {
            // Randomly pick two alleles
            Allele a1 = randomAllele();
            Allele a2 = randomAllele();
            data.setGenePair(trait, new GenePair(a1, a2));
        }
        return data;
    }

    /**
     * 基因混合（繁殖遗传）
     */
    public GeneData mixGenes(GeneData parent1, GeneData parent2) {
        GeneData child = new GeneData();
        for (Trait trait : Trait.values()) {
            GenePair pair1 = parent1.getGenePair(trait);
            GenePair pair2 = parent2.getGenePair(trait);

            // Mendelian inheritance: Pick one allele from each parent
            Allele a1 = ThreadLocalRandom.current().nextBoolean() ? pair1.getFirst() : pair1.getSecond();
            Allele a2 = ThreadLocalRandom.current().nextBoolean() ? pair2.getFirst() : pair2.getSecond();
            
            // Natural Mutation logic (0.1% chance per allele)
            if (ThreadLocalRandom.current().nextDouble() < 0.001) {
                a1 = mutate(a1);
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.001) {
                a2 = mutate(a2);
            }
            
            child.setGenePair(trait, new GenePair(a1, a2));
        }
        return child;
    }

    /**
     * Trigger a forced mutation check on an existing animal.
     * Used for stress/environmental degradation.
     * @param animal The animal to check
     * @param chance Probability to mutate (e.g. 0.01 for 1%)
     * @param malignant If true, mutation will only degrade (lower value)
     */
    public void attemptExistingMutation(LivingEntity animal, double chance, boolean malignant) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        
        GeneData genes = getGenesFromEntity(animal);
        if (genes == null) return;
        
        // Pick a random trait to mutate
        Trait[] traits = Trait.values();
        Trait targetTrait = traits[ThreadLocalRandom.current().nextInt(traits.length)];
        GenePair pair = genes.getGenePair(targetTrait);
        
        // Pick one allele
        boolean first = ThreadLocalRandom.current().nextBoolean();
        Allele original = first ? pair.getFirst() : pair.getSecond();
        
        Allele mutated;
        if (malignant) {
            mutated = mutateMalignant(original);
        } else {
            mutated = mutate(original);
        }
        
        // Update GenePair
        GenePair newPair;
        if (first) {
            newPair = new GenePair(mutated, pair.getSecond());
        } else {
            newPair = new GenePair(pair.getFirst(), mutated);
        }
        genes.setGenePair(targetTrait, newPair);
        
        // Save back to entity
        saveGenesToEntity(animal, genes);
        
        // Visual feedback
        animal.getWorld().spawnParticle(org.bukkit.Particle.CAMPFIRE_SIGNAL_SMOKE, animal.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);
    }

    private Allele mutate(Allele original) {
        int val = original.getValue();
        // 50% chance to improve, 50% to degrade
        int change = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        return getAlleleByValue(val + change, original);
    }
    
    private Allele mutateMalignant(Allele original) {
        int val = original.getValue();
        // Always degrade (-1)
        return getAlleleByValue(val - 1, original);
    }
    
    private Allele getAlleleByValue(int newVal, Allele original) {
         // Skip 0 (no allele has value 0)
        if (newVal == 0) {
            // If we were at 1 and went to 0 -> go to -1
            // If we were at -1 and went to 0 -> go to 1
            newVal = (original.getValue() > 0) ? -1 : 1; 
            // Wait, logic check: 
            // 1 -> 0 -> -1 (Degrade)
            // -1 -> 0 -> 1 (Improve)
            // If I am mutating randomly, the 'change' direction matters.
            // But here I only know the target 'newVal' is 0.
            // If original was 1, newVal 0 means we subtracted. So continue to -1.
            if (original.getValue() == 1) newVal = -1;
            if (original.getValue() == -1) newVal = 1;
        }
        
        // Clamp to -4 to 4
        if (newVal > 4) newVal = 4;
        if (newVal < -4) newVal = -4;
        
        // Find allele with this value
        for (Allele a : Allele.values()) {
            if (a.getValue() == newVal) return a;
        }
        return original;
    }

    private Allele randomAllele() {
        Allele[] values = Allele.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    // ==========================================
    // 实体逻辑 (LivingEntity PDC)
    // ==========================================

    /**
     * 从实体中读取基因数据。
     * 如果实体没有基因数据，返回一个默认的（未初始化的）GeneData，
     * 或者在这里初始化它？通常我们只读取，初始化交给生成监听器。
     */
    public GeneData getGenesFromEntity(LivingEntity entity) {
        if (entity == null) return new GeneData();
        
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        
        boolean identified = pdc.has(IDENTIFIED_KEY, PersistentDataType.BYTE) && 
                             pdc.get(IDENTIFIED_KEY, PersistentDataType.BYTE) == 1;

        GeneData data = new GeneData();
        data.setIdentified(identified);
        
        for (Trait trait : Trait.values()) {
            NamespacedKey key = TRAIT_KEYS.get(trait);
            if (pdc.has(key, PersistentDataType.STRING)) {
                String val = pdc.get(key, PersistentDataType.STRING);
                data.setGenePair(trait, parseGenePair(val));
            }
        }
        
        // 如果没有数据，可能需要随机生成？或者由调用者决定
        // 这里返回默认全0基因
        return data;
    }

    /**
     * 将基因数据写入实体。
     */
    public void saveGenesToEntity(LivingEntity entity, GeneData data) {
        if (entity == null) return;
        
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        
        // 1. 保存鉴定状态
        pdc.set(IDENTIFIED_KEY, PersistentDataType.BYTE, (byte) (data.isIdentified() ? 1 : 0));
        
        // 2. 保存基因数据
        for (Trait trait : Trait.values()) {
            GenePair pair = data.getGenePair(trait);
            String val = pair.getFirst().getCode(trait) + ":" + pair.getSecond().getCode(trait);
            pdc.set(TRAIT_KEYS.get(trait), PersistentDataType.STRING, val);
        }
    }
    
    // ==========================================
    // 物品逻辑 (DNA采样器 / 刷怪蛋)
    // ==========================================

    /**
     * 从物品中读取基因数据
     */
    public GeneData getGenesFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new GeneData(); 
        }
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        boolean identified = pdc.has(IDENTIFIED_KEY, PersistentDataType.BYTE) && 
                             pdc.get(IDENTIFIED_KEY, PersistentDataType.BYTE) == 1;

        GeneData data = new GeneData();
        data.setIdentified(identified);
        
        for (Trait trait : Trait.values()) {
            NamespacedKey key = TRAIT_KEYS.get(trait);
            if (pdc.has(key, PersistentDataType.STRING)) {
                String val = pdc.get(key, PersistentDataType.STRING);
                data.setGenePair(trait, parseGenePair(val));
            }
        }
        
        return data;
    }

    /**
     * 将基因数据保存到物品
     */
    public void saveGenesToItem(ItemStack item, GeneData data) {
        if (item == null || item.getType().isAir()) return;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        pdc.set(IDENTIFIED_KEY, PersistentDataType.BYTE, (byte) (data.isIdentified() ? 1 : 0));
        
        for (Trait trait : Trait.values()) {
            GenePair pair = data.getGenePair(trait);
            String val = pair.getFirst().getCode(trait) + ":" + pair.getSecond().getCode(trait);
            pdc.set(TRAIT_KEYS.get(trait), PersistentDataType.STRING, val);
        }
        
        item.setItemMeta(meta);
    }

    // 解析 "V1:v1" -> GenePair
    private GenePair parseGenePair(String str) {
        if (str == null || !str.contains(":")) return new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1);
        String[] parts = str.split(":");
        Allele a1 = parseAllele(parts[0]);
        Allele a2 = parseAllele(parts[1]);
        return new GenePair(a1, a2);
    }
    
    private Allele parseAllele(String code) {
        // Code format: V1, v1, V2, v2 etc.
        // Or old format support if needed (not needed for new project)
        
        if (code == null || code.length() < 2) return Allele.DOMINANT_1;
        
        // Remove gene letter (first char)
        String valStr = code.substring(1);
        try {
            int val = Integer.parseInt(valStr);
            // Reconstruct based on case of first char?
            // Actually Allele just stores value. 
            // V1 -> 1, v1 -> 1? No.
            // Allele enum has DOMINANT_1(1), RECESSIVE_1(-1).
            // But wait, Allele.getCode() returns "V1" for DOMINANT_1 and "v1" for RECESSIVE_1 (abs value).
            // So if code starts with uppercase, it is positive. If lowercase, negative.
            
            boolean isDominant = Character.isUpperCase(code.charAt(0));
            if (!isDominant) val = -val;
            
            for (Allele a : Allele.values()) {
                if (a.getValue() == val) return a;
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return Allele.DOMINANT_1;
    }

    /**
     * 更新物品 Lore 以显示基因信息
     */
    public void updateGeneLore(ItemStack item, GeneData genes) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        
        if (genes.isIdentified()) {
            lore.add(net.kyori.adventure.text.Component.text("§7已分析的 DNA 样本"));
            lore.add(net.kyori.adventure.text.Component.text("§8----------------"));
            
            for (Trait trait : Trait.values()) {
                GenePair pair = genes.getGenePair(trait);
                double score = pair.getPhenotypeValue();
                String adj = trait.getAdjective(score);
                if (adj == null) adj = "普通";
                
                String color = (score > 0) ? "§a" : ((score < 0) ? "§c" : "§f");
                
                // e.g. "生长势: [V2, v1] (活力)"
                String line = String.format("§7%s: §f[%s, %s] %s(%s)", 
                    trait.getName(), 
                    pair.getFirst().getCode(trait), 
                    pair.getSecond().getCode(trait), 
                    color, 
                    adj
                );
                lore.add(net.kyori.adventure.text.Component.text(line));
            }
            
            // Star Rating (Optional)
             // int stars = ...
        } else {
            lore.add(net.kyori.adventure.text.Component.text("§7未分析的 DNA 样本"));
            lore.add(net.kyori.adventure.text.Component.text("§8请使用遗传分析仪进行鉴定"));
        }
        
        meta.lore(lore);
        item.setItemMeta(meta);
    }
}
