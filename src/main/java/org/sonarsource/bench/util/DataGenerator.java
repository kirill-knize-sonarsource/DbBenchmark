package org.sonarsource.bench.util;

import org.sonarsource.bench.model.Issue;
import org.sonarsource.bench.model.IssueFlow;
import org.sonarsource.bench.model.IssueLocation;
import org.sonarsource.bench.model.QuickFix;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class DataGenerator {
    private final Random rnd;
    private final String[] ruleKeys;
    private final String[] severities = {"BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO"};

    public DataGenerator(long seed, String[] ruleKeys) {
        this.rnd = new Random(seed);
        this.ruleKeys = ruleKeys;
    }

    public List<Issue> generate(int count) {
        List<Issue> list = new ArrayList<>(count);
        long baseTime = Instant.now().toEpochMilli();
        for (int i = 0; i < count; i++) {
            String id = UUID.randomUUID().toString();
            String ruleKey = ruleKeys[rnd.nextInt(ruleKeys.length)];
            String severity = severities[rnd.nextInt(severities.length)];
            String filePath = "/project/module/src/main/java/com/example/Foo" + rnd.nextInt(1_000) + ".java";
            int line = 1 + rnd.nextInt(500);
            String message = "Issue on line " + line + " for rule " + ruleKey + " lorem ipsum dolor sit amet " + rnd.nextInt(1000);
            String assignee = rnd.nextBoolean() ? ("user" + rnd.nextInt(50)) : null;
            List<String> tags = randomTags();
            long created = baseTime - rnd.nextInt(1000 * 60 * 60 * 24 * 365);

            Issue issue = new Issue(id, ruleKey, severity, message, filePath, line, created, assignee, tags);

            // Primary location is initialized by Issue constructor; optionally enrich offsets
            IssueLocation pl = issue.getPrimaryLocation();
            if (pl != null) {
                pl.setStartOffset(0);
                pl.setEndOffset(20 + rnd.nextInt(80));
            }

            // Random flows
            int flowCount = rnd.nextInt(3); // 0..2 flows
            List<IssueFlow> flows = new ArrayList<>(flowCount);
            for (int f = 0; f < flowCount; f++) {
                int locCount = 1 + rnd.nextInt(3);
                List<IssueLocation> locs = new ArrayList<>(locCount);
                for (int l = 0; l < locCount; l++) {
                    int lno = 1 + rnd.nextInt(500);
                    locs.add(new IssueLocation(filePath, lno, lno, null, null, "Flow step " + (l + 1)));
                }
                flows.add(new IssueFlow(locs));
            }
            issue.setFlows(flows);

            // Random quick fixes
            int qfCount = rnd.nextInt(3); // 0..2
            List<QuickFix> qfs = new ArrayList<>(qfCount);
            for (int q = 0; q < qfCount; q++) {
                List<IssueLocation> qlocs = new ArrayList<>();
                qlocs.add(new IssueLocation(filePath, line, line, 0, 10 + rnd.nextInt(30), "Apply fix " + (q + 1)));
                qfs.add(new QuickFix("Quick fix suggestion " + (q + 1), qlocs));
            }
            issue.setQuickFixes(qfs);

            list.add(issue);
        }
        return list;
    }

    private List<String> randomTags() {
        String[] pool = {"security", "bug", "vulnerability", "code-smell", "performance", "style", "unused", "nullability"};
        int n = 1 + rnd.nextInt(4);
        List<String> tags = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tags.add(pool[rnd.nextInt(pool.length)]);
        }
        return tags;
    }
}
