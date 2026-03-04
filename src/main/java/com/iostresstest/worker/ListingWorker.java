package com.iostresstest.worker;

import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repeatedly picks a random directory within the target and performs a listing (Files.list),
 * recording count and latency per operation.
 */
public class ListingWorker implements Runnable {

    private final Path directory;
    private final MetricsRegistry metrics;
    private final AtomicBoolean running;
    private final Random rng = new Random();

    public ListingWorker(Path directory, MetricsRegistry metrics, AtomicBoolean running) {
        this.directory = directory;
        this.metrics   = metrics;
        this.running   = running;
    }

    @Override
    public void run() {
        List<Path> dirs = scanDirectories(directory);
        if (dirs.isEmpty()) {
            dirs = new ArrayList<>();
            dirs.add(directory);
        }

        while (running.get()) {
            Path dir = dirs.get(rng.nextInt(dirs.size()));
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
            metrics.recordError(OperationType.DIR_LIST);
        }
    }

    private static List<Path> scanDirectories(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isDirectory).collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}
