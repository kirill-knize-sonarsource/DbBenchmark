package org.sonarsource.bench.util;

public class Stopwatch {
    private long start;
    private long end;

    public static Stopwatch startNew() {
        Stopwatch sw = new Stopwatch();
        sw.start();
        return sw;
        
    }

    public void start() {
        start = System.nanoTime();
        end = 0L;
    }

    public long stop() {
        end = System.nanoTime();
        return elapsedMillis();
    }

    public long elapsedMillis() {
        long t = (end == 0L ? System.nanoTime() : end) - start;
        return t / 1_000_000L;
    }
}
