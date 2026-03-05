package com.iostresstest.worker;

import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Repeatedly picks a random directory within the target and performs a listing (Files.list),
 * recording count and latency per operation.
 */
public class ListingWorker implements Runnable {

    private final MetricsRegistry metrics;
    private final AtomicBoolean running;
    private final List<Path> dirs;
    private final Random rng = new Random();

    public ListingWorker(MetricsRegistry metrics, AtomicBoolean running, List<Path> dirs) {
        this.metrics = metrics;
        this.running = running;
        this.dirs    = dirs;
    }

    @Override
    public void run() {

        while (running.get()) {
            Path dir = dirs.get(rng.nextInt(dirs.size()));  // list is immutable, safe to read
            listDirectory(dir);
        }
    }

    private void listDirectory(Path dir) {
        long start = System.nanoTime();
        try (Stream<Path> entries = Files.list(dir)) {
            long count = entries.count();
            // Record entry count as "bytes" so throughput is meaningful as entries/s in the UI
            metrics.record(OperationType.DIR_LIST, System.nanoTime() - start, count);
        } catch (IOException e) {
            metrics.recordError(OperationType.DIR_LIST, e);
        }
    }

}
