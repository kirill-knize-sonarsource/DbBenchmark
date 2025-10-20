package org.sonarsource.bench;

import org.sonarsource.bench.db.H2IssueRepository;
import org.sonarsource.bench.db.IssueRepository;
import org.sonarsource.bench.db.SqliteIssueRepository;
import org.sonarsource.bench.model.Issue;
import org.sonarsource.bench.util.BenchmarkConfig;
import org.sonarsource.bench.util.DataGenerator;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Runner for simulating abrupt termination scenarios to assess DB corruption (H2 vs SQLite).
 * <p>
 * Usage examples:
 * - Prepare a DB and wait to allow external kill:
 * java ... org.sonarsource.bench.CrashScenarioRunner mode=prepare db=sqlite path=/tmp/sqlite-oom.db items=50000 batch=1000 waitSec=600
 * Then kill the process (e.g., kill -9 <pid>) while it waits.
 * <p>
 * - Verify the DB after a crash/kill:
 * java ... org.sonarsource.bench.CrashScenarioRunner mode=verify db=sqlite path=/tmp/sqlite-oom.db
 * <p>
 * Notes:
 * - When mode=prepare, the runner DOES NOT call repo.close(); this intentionally leaves the DB in-flight.
 * - Provide an explicit file path via path=... so that the file persists across processes.
 */
public class CrashScenarioRunner {
  public static void main(String[] args) throws Exception {
    Args a = Args.parse(args);
    if (a.mode == null || a.db == null || a.path == null) {
      System.out.println("CrashScenarioRunner requires: mode=prepare|verify db=h2|sqlite path=/path/to/db [items=N batch=B rule=java:S1234 waitSec=T]");
      return;
    }

    if ("prepare".equalsIgnoreCase(a.mode)) {
      doPrepare(a);
    } else if ("verify".equalsIgnoreCase(a.mode)) {
      doVerify(a);
    } else {
      System.out.println("Unknown mode: " + a.mode);
    }
  }

  private static void doPrepare(Args a) throws Exception {
    System.out.println("CrashScenario PREPARE: db=" + a.db + ", path=" + a.path + ", items=" + a.items + ", batch=" + a.batch + ", rule=" + a.rule + ", sleepMs=" + a.sleepMs);
    IssueRepository repo = createRepo(a);
    // Generate deterministic data
    String[] ruleKeys = {"java:S100", "java:S101", "java:S1854", "java:S106", "java:S1234", "java:S121"};
    DataGenerator gen = new DataGenerator(42L, ruleKeys);
    List<Issue> data = gen.generate(a.items);

    System.out.println("PID=" + getPidSafe());
    System.out.println("WRITES_STARTING at " + java.time.Instant.now());
    repo.init();
    if (a.sleepMs > 0) {
      int n = data.size();
      int idx = 0;
      boolean announced = false;
      while (idx < n) {
        int to = Math.min(idx + Math.max(1, a.batch), n);
        List<Issue> chunk = data.subList(idx, to);
        if (!announced) {
          System.out.println("WRITES_STARTED");
          announced = true;
        }
        repo.insertAll(chunk);
        idx = to;
        try {
          Thread.sleep(a.sleepMs);
        } catch (InterruptedException ignored) {
        }
      }
    } else {
      repo.insertAll(data);
    }
    // Intentionally do not close the repository here to simulate in-flight state.
    System.out.println("Prepared DB at: " + a.path.getAbsolutePath());
    System.out.println("If you want to simulate a crash, kill this process now.");
    if (a.waitSec > 0) {
      System.out.println("Waiting for " + a.waitSec + " seconds...");
      try {
        Thread.sleep(a.waitSec * 1000L);
      } catch (InterruptedException ignored) {
      }
    }
    // Exit without calling close(); in real crash this wouldn't run, but when not killed, JVM cleanup will occur.
  }

  private static void doVerify(Args a) throws Exception {
    System.out.println("CrashScenario VERIFY: db=" + a.db + ", path=" + a.path);
    IssueRepository repo = createRepo(a);
    try {
      repo.init();
      int count = repo.readAll().size();
      System.out.println("ReadAll succeeded. items=" + count);
      // Try a simple search
      int found = repo.searchByRule(a.rule).size();
      System.out.println("Search(rule='" + a.rule + "') found=" + found);

      // Assert file integrity where supported
      if ("sqlite".equalsIgnoreCase(a.db)) {
        assertSqliteIntegrityOk(a.path);
      } else if ("h2".equalsIgnoreCase(a.db)) {
        // Best-effort check: if we could open and query, assume not corrupted for H2
        System.out.println("H2 integrity: basic open/query succeeded (no explicit integrity_check available)");
      }

      System.out.println("Verification assertions passed.");
    } catch (Throwable t) {
      System.out.println("Verification failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
      t.printStackTrace(System.out);
      throw (t instanceof Exception) ? (Exception) t : new RuntimeException(t);
    } finally {
      try {
        repo.close();
      } catch (Exception ignored) {
      }
    }
  }

  private static void assertSqliteIntegrityOk(File path) throws Exception {
    // Direct JDBC connection to run PRAGMA integrity_check
    String url = "jdbc:sqlite:" + path.getAbsolutePath();
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("sqlite-jdbc driver not found on classpath", e);
    }
    try (java.sql.Connection c = java.sql.DriverManager.getConnection(url);
         java.sql.Statement st = c.createStatement();
         java.sql.ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
      String result = rs.next() ? rs.getString(1) : null;
      System.out.println("SQLite PRAGMA integrity_check = " + result);
      if (result == null || !"ok".equalsIgnoreCase(result.trim())) {
        throw new IllegalStateException("SQLite file integrity_check failed: " + result);
      }
    }
  }

  private static IssueRepository createRepo(Args a) {
    String db = a.db.toLowerCase(Locale.ROOT);
    String path = a.path.getAbsolutePath();
    switch (db) {
      case "h2":
        return new H2IssueRepository(path);
      case "sqlite":
        return new SqliteIssueRepository(path);
      default:
        throw new IllegalArgumentException("Unsupported db: " + a.db);
    }
  }

  private static String getPidSafe() {
    try {
      String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
      int idx = jvmName.indexOf('@');
      return (idx > 0 ? jvmName.substring(0, idx) : jvmName);
    } catch (Throwable t) {
      return "unknown";
    }
  }

  private static class Args {
    String mode;
    String db;
    File path;
    int items = BenchmarkConfig.defaultConfig().itemCount;
    int batch = BenchmarkConfig.defaultConfig().batchSize; // currently unused by repos, kept for parity
    String rule = BenchmarkConfig.defaultConfig().searchRuleKey;
    int waitSec = 0;
    int sleepMs = 0;

    static Args parse(String[] args) {
      Args a = new Args();
      for (String s : args) {
        String[] kv = s.split("=", 2);
        if (kv.length != 2) continue;
        String key = kv[0].toLowerCase(Locale.ROOT);
        String val = kv[1];
        switch (key) {
          case "mode":
            a.mode = val;
            break;
          case "db":
            a.db = val;
            break;
          case "path":
            a.path = new File(val);
            break;
          case "items":
            try {
              a.items = Integer.parseInt(val);
            } catch (NumberFormatException ignored) {
            }
            break;
          case "batch":
            try {
              a.batch = Integer.parseInt(val);
            } catch (NumberFormatException ignored) {
            }
            break;
          case "rule":
            a.rule = val;
            break;
          case "waitsec":
            try {
              a.waitSec = Integer.parseInt(val);
            } catch (NumberFormatException ignored) {
            }
            break;
          case "sleepms":
            try {
              a.sleepMs = Integer.parseInt(val);
            } catch (NumberFormatException ignored) {
            }
            break;
        }
      }
      return a;
    }
  }
}
