package org.sonarsource.bench;

import org.sonarsource.bench.db.*;
import org.sonarsource.bench.model.Issue;
import org.sonarsource.bench.util.BenchmarkConfig;
import org.sonarsource.bench.util.DataGenerator;
import org.sonarsource.bench.util.Stopwatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        BenchmarkConfig cfg = parseArgs(args);
        System.out.println("Database Benchmark");
        System.out.println("Items=" + cfg.itemCount + ", batchSize=" + cfg.batchSize + ", searchRuleKey=" + cfg.searchRuleKey);

        String[] ruleKeys = {"java:S100", "java:S101", "java:S1854", "java:S106", "java:S1234", "java:S121"};
        DataGenerator gen = new DataGenerator(42L, ruleKeys);
        List<Issue> data = gen.generate(cfg.itemCount);

        List<IssueRepository> repos = List.of(
                new H2IssueRepository(),
                new HsqldbIssueRepository(),
                new DerbyIssueRepository(),
                //new MapDbIssueRepository(),
                new NitriteIssueRepository()
        );

        for (IssueRepository repo : repos) {
            runBench(repo, new ArrayList<>(data), cfg);
            System.out.println();
        }
    }

    private static void runBench(IssueRepository repo, List<Issue> data, BenchmarkConfig cfg) throws Exception {
        System.out.println("== " + repo.name() + " ==");
        repo.init();

        // write
        Stopwatch sw = Stopwatch.startNew();
        repo.insertAll(data);
        long writeMs = sw.stop();
        System.out.println("Write: " + writeMs + " ms");

        // read all
        sw.start();
        List<Issue> all = repo.readAll();
        long readMs = sw.stop();
        System.out.println("ReadAll: " + readMs + " ms (" + all.size() + ")");

        // search by rule key
        sw.start();
        List<Issue> found = repo.searchByRule(cfg.searchRuleKey);
        long searchMs = sw.stop();
        System.out.println("Search(rule='" + cfg.searchRuleKey + "'): " + searchMs + " ms (" + found.size() + ")");

        repo.close();
    }

    private static BenchmarkConfig parseArgs(String[] args) {
        int n = BenchmarkConfig.defaultConfig().itemCount;
        int batch = BenchmarkConfig.defaultConfig().batchSize;
        String rule = BenchmarkConfig.defaultConfig().searchRuleKey;
        for (String a : args) {
            String[] kv = a.split("=");
            if (kv.length != 2) continue;
            switch (kv[0].toLowerCase(Locale.ROOT)) {
                case "items": n = Integer.parseInt(kv[1]); break;
                case "batch": batch = Integer.parseInt(kv[1]); break;
                case "rule": rule = kv[1]; break;
            }
        }
        return new BenchmarkConfig(n, batch, 1, rule);
    }
}
