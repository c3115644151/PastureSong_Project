package com.example.pasturesong.genetics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基因数据 (Gene Data)
 * 存储一只动物的所有遗传信息。
 * 核心结构: Map<Trait, GenePair>
 */
public class GeneData {

    private final Map<Trait, GenePair> genes;
    private boolean identified;

    public GeneData() {
        this.genes = new HashMap<>();
        this.identified = false;
        initializeDefaults();
    }
    
    public GeneData(Map<Trait, GenePair> genes, boolean identified) {
        this.genes = new HashMap<>(genes);
        this.identified = identified;
        // 确保所有性状都存在
        for (Trait trait : Trait.values()) {
            this.genes.putIfAbsent(trait, new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1));
        }
    }

    private void initializeDefaults() {
        // 默认全是普通型 (A1 + a1 = 0)
        for (Trait trait : Trait.values()) {
            genes.put(trait, new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1));
        }
    }
    
    public boolean isDefault() {
        for (Trait trait : Trait.values()) {
            GenePair pair = genes.get(trait);
            // Default is DOMINANT_1 (1) + RECESSIVE_1 (-1)
            if (pair.getFirst() != Allele.DOMINANT_1 || pair.getSecond() != Allele.RECESSIVE_1) {
                return false;
            }
        }
        return true;
    }

    public GenePair getGenePair(Trait trait) {
        return genes.getOrDefault(trait, new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1));
    }

    public void setGenePair(Trait trait, GenePair pair) {
        genes.put(trait, pair);
    }

    public boolean isIdentified() {
        return identified;
    }

    public void setIdentified(boolean identified) {
        this.identified = identified;
    }
    
    public Map<Trait, GenePair> getAllGenes() {
        return new HashMap<>(genes);
    }
    
    // --- 随机生成逻辑 ---
    
    /**
     * 随机生成初始基因 (用于自然生成/商店蛋)
     */
    public void randomize() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        for (Trait trait : Trait.values()) {
            genes.put(trait, generateRandomPair(random));
        }
    }
    
    public static GenePair generateRandomPair(ThreadLocalRandom random) {
        double roll = random.nextDouble();
        
        // 60% 普通: A1 + a1 = 0
        if (roll < 0.60) {
            return new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_1);
        }
        
        // 20% 轻微偏差 (±1 ~ ±2)
        else if (roll < 0.80) {
            boolean positive = random.nextBoolean();
            if (positive) {
                // [A1, A1] (2) or [A2, a1] (1)
                return random.nextBoolean() ? 
                       new GenePair(Allele.DOMINANT_1, Allele.DOMINANT_1) :
                       new GenePair(Allele.DOMINANT_2, Allele.RECESSIVE_1);
            } else {
                // [a1, a1] (-2) or [A1, a2] (-1)
                return random.nextBoolean() ?
                       new GenePair(Allele.RECESSIVE_1, Allele.RECESSIVE_1) :
                       new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_2);
            }
        }
        
        // 15% 偏差II (±3 ~ ±4)
        else if (roll < 0.95) {
             boolean positive = random.nextBoolean();
             if (positive) {
                 return random.nextBoolean() ?
                        new GenePair(Allele.DOMINANT_2, Allele.DOMINANT_2) : // 4
                        new GenePair(Allele.DOMINANT_3, Allele.RECESSIVE_1); // 2
             } else {
                 return random.nextBoolean() ?
                        new GenePair(Allele.RECESSIVE_2, Allele.RECESSIVE_2) : // -4
                        new GenePair(Allele.DOMINANT_1, Allele.RECESSIVE_3); // -2
             }
        }
        
        // 5% 极端 (±5 ~ ±6)
        else {
             boolean positive = random.nextBoolean();
             if (positive) {
                 return new GenePair(Allele.DOMINANT_3, Allele.DOMINANT_3); // 6
             } else {
                 return new GenePair(Allele.RECESSIVE_3, Allele.RECESSIVE_3); // -6
             }
        }
    }
}
