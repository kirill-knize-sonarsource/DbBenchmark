package org.sonarsource.bench.util;

public class BenchmarkConfig {
    public final int itemCount;
    public final int batchSize;
    public final int warmupBatches;
    public final String searchRuleKey;

    public BenchmarkConfig(int itemCount, int batchSize, int warmupBatches, String searchRuleKey) {
        this.itemCount = itemCount;
        this.batchSize = batchSize;
        this.warmupBatches = warmupBatches;
        this.searchRuleKey = searchRuleKey;
    }

    public static BenchmarkConfig defaultConfig() {
        return new BenchmarkConfig(1_000_000, 5_000, 10, "java:S1234");
    }
}
