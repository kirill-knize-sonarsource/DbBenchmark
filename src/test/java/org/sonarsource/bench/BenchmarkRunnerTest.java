package org.sonarsource.bench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BenchmarkRunnerTest {

    @Test
    void benchmarkRunner_smokeTest_shouldNotThrow() {
        String[] args = new String[] {"items=2000", "batch=500", "rule=java:S1234"};
        assertDoesNotThrow(() -> BenchmarkRunner.main(args));
    }
}
