package org.sonarsource.bench;

import org.junit.jupiter.api.Test;
import org.sonarsource.bench.db.SqliteIssueRepository;
import org.sonarsource.bench.db.IssueRepository;
import org.sonarsource.bench.util.DataGenerator;
import org.sonarsource.bench.model.Issue;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CrashScenarioRunnerTest {

  @Test
  void crashRunner_prepare_sqlite_smoke() {
    File f;
    try {
      f = File.createTempFile("sqlite-crash-test", ".db");
      if (f.exists()) f.delete();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    String[] args = new String[]{
      "mode=prepare", "db=sqlite", "path=" + f.getAbsolutePath(), "items=1000", "batch=200", "waitSec=0"
    };
    assertDoesNotThrow(() -> CrashScenarioRunner.main(args));
    // Cleanup created file
    try {
      new File(f.getAbsolutePath()).delete();
    } catch (Exception ignored) {
    }
  }

  @Test
  void crashRunner_prepare_h2_smoke() {
    File f;
    try {
      f = File.createTempFile("h2-crash-test", ".db");
      if (f.exists()) f.delete();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    String[] args = new String[]{
      "mode=prepare", "db=h2", "path=" + f.getAbsolutePath(), "items=1000", "batch=200", "waitSec=0"
    };
    assertDoesNotThrow(() -> CrashScenarioRunner.main(args));
    // Cleanup H2 files (may create .mv.db etc.)
    try {
      new File(f.getAbsolutePath() + ".mv.db").delete();
    } catch (Exception ignored) {
    }
    try {
      new File(f.getAbsolutePath()).delete();
    } catch (Exception ignored) {
    }
  }

  @Test
  void crashRunner_verify_sqlite_integrity_ok() throws Exception {
    // Prepare a SQLite DB with some data and close it
    File f;
    try {
      f = File.createTempFile("sqlite-crash-verify", ".db");
      if (f.exists()) f.delete();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    IssueRepository repo = new SqliteIssueRepository(f.getAbsolutePath());
    String[] ruleKeys = {"java:S100", "java:S101"};
    DataGenerator gen = new DataGenerator(7L, ruleKeys);
    List<Issue> data = gen.generate(50);
    repo.init();
    repo.insertAll(data);
    repo.close();

    // Now run verify mode which asserts PRAGMA integrity_check == 'ok'
    String[] verifyArgs = new String[]{"mode=verify", "db=sqlite", "path=" + f.getAbsolutePath(), "rule=java:S100"};
    assertDoesNotThrow(() -> CrashScenarioRunner.main(verifyArgs));

    // Cleanup
    try {
      new File(f.getAbsolutePath()).delete();
    } catch (Exception ignored) {
    }
  }

  @Test
  void crashRunner_full_sqlite_kill_child_during_writes_then_verify_integrity() throws Exception {
    // Create DB path
    File f;
    try {
      f = File.createTempFile("sqlite-crash-full", ".db");
      if (f.exists()) f.delete();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    String cp = System.getProperty("java.class.path");
    ProcessBuilder pb = new ProcessBuilder(
      javaBin,
      "-cp", cp,
      "org.sonarsource.bench.CrashScenarioRunner",
      "mode=prepare",
      "db=sqlite",
      "path=" + f.getAbsolutePath(),
      "items=5000",
      "batch=200",
      "sleepms=50"
    );
    pb.redirectErrorStream(true);
    Process p = pb.start();

    // Wait until the child reports it started writing, then kill it
    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
      long deadline = System.currentTimeMillis() + 15000L;
      String line;
      boolean started = false;
      while (System.currentTimeMillis() < deadline) {
        line = br.readLine();
        if (line == null) {
          try {
            Thread.sleep(25);
          } catch (InterruptedException ignored) {
          }
          continue;
        }
        if (line.contains("WRITES_STARTED")) {
          started = true;
          break;
        }
      }
      if (!started) {
        // Best effort: dump remaining output for easier diagnosis
        StringBuilder rest = new StringBuilder();
        while ((line = br.readLine()) != null) rest.append(line).append('\n');
        throw new IllegalStateException("Child did not start writes in time. Output=\n" + rest);
      }
    }

    // Give it a tiny bit of time to be mid-flight
    try {
      Thread.sleep(150);
    } catch (InterruptedException ignored) {
    }
    p.destroyForcibly();
    try {
      p.waitFor();
    } catch (InterruptedException ignored) {
    }

    // Now verify integrity on the same DB path
    String[] verifyArgs = new String[]{"mode=verify", "db=sqlite", "path=" + f.getAbsolutePath(), "rule=java:S100"};
    assertDoesNotThrow(() -> CrashScenarioRunner.main(verifyArgs));

    // Cleanup DB file
    try {
      new File(f.getAbsolutePath()).delete();
    } catch (Exception ignored) {
    }
  }
}
