package com.example.pasturesong.genetics;

/**
 * 性状定义 (Trait Definition)
 * 定义了动物可能拥有的所有特性类型。
 */
public enum Trait {
    /**
     * V (Vigor) - 生长势
     * 决定幼年到成熟的时间。
     */
    VITALITY("生长势", "决定幼年到成熟的时间", 
             "茁壮", "挺拔", "魁梧", "巨兽", 
             "孱弱", "迟滞", "侏儒", "枯萎", 'V'),
    
    /**
     * Q (Quality) - 品质
     * 决定肉类的油花分布或牛奶的脂肪含量。
     */
    QUALITY("品质", "决定产物品质", 
            "丰腴", "霜降", "丝滑", "极品", 
            "干柴", "腥臊", "粗糙", "劣质", 'Q'),

    /**
     * R (Resistance) - 抗性
     * 决定应激阈值和对疾病的免疫力。
     */
    RESISTANCE("抗性", "决定应激阈值和疾病免疫力", 
               "粗壮", "厚皮", "钢筋", "不坏", 
               "娇气", "易感", "玻璃", "纸糊", 'R'),

    /**
     * F (Fertility) - 多产性
     * 决定单次产蛋量或双胞胎概率。
     */
    FERTILITY("多产性", "决定单次产量或双胞胎概率", 
              "繁衍", "满巢", "英雄", "奇迹", 
              "孤寡", "贫瘠", "绝嗣", "虚无", 'F');

    private final String name;
    private final String description;
    
    // Positive Tiers 1-4
    private final String posT1; 
    private final String posT2; 
    private final String posT3; 
    private final String posT4; 
    
    // Negative Tiers 1-4
    private final String negT1; 
    private final String negT2; 
    private final String negT3; 
    private final String negT4; 
    
    private final char geneLetter;

    Trait(String name, String description, 
          String posT1, String posT2, String posT3, String posT4,
          String negT1, String negT2, String negT3, String negT4, 
          char geneLetter) {
        this.name = name;
        this.description = description;
        this.posT1 = posT1;
        this.posT2 = posT2;
        this.posT3 = posT3;
        this.posT4 = posT4;
        this.negT1 = negT1;
        this.negT2 = negT2;
        this.negT3 = negT3;
        this.negT4 = negT4;
        this.geneLetter = geneLetter;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public char getGeneLetter() { return geneLetter; }

    /**
     * 获取性状形容词
     * @param score 基因型分数
     * @return 形容词，若未达到阈值(Abs<3)返回 null
     */
    public String getAdjective(double score) {
        double abs = Math.abs(score);
        if (abs < 3.0) return null;

        if (score > 0) {
            if (abs >= 9.0) return posT4;
            if (abs >= 7.0) return posT3;
            if (abs >= 5.0) return posT2;
            return posT1;
        } else {
            if (abs >= 9.0) return negT4;
            if (abs >= 7.0) return negT3;
            if (abs >= 5.0) return negT2;
            return negT1;
        }
    }
}
