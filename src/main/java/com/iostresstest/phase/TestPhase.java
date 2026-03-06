package com.iostresstest.phase;

import com.iostresstest.cli.WorkerSpec;
import com.iostresstest.corpus.CorpusManager;
import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.ui.AnsiDashboard;
import com.iostresstest.worker.WorkerGroup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs all worker groups for the configured duration while the ANSI dashboard refreshes live.
 * A shutdown hook ensures a clean stop on Ctrl+C.
 */
public class TestPhase {

    private static final long DASHBOARD_REFRESH_MS = 500;

    private final Duration duration;
    private final List<WorkerSpec> workerSpecs;
    private final long fileSizeMin;
    private final long fileSizeMax;
    private final MetricsRegistry metrics;
    private final CorpusManager corpusManager;
    private final AnsiDashboard dashboard;

    public TestPhase(Duration duration, List<WorkerSpec> workerSpecs,
                     long fileSizeMin, long fileSizeMax,
                     MetricsRegistry metrics, CorpusManager corpusManager,
                     AnsiDashboard dashboard) {
        this.duration      = duration;
        this.workerSpecs   = workerSpecs;
        this.fileSizeMin   = fileSizeMin;
        this.fileSizeMax   = fileSizeMax;
        this.metrics       = metrics;
        this.corpusManager = corpusManager;
        this.dashboard     = dashboard;
    }

    public PhaseResult run() {
        AtomicBoolean running = new AtomicBoolean(true);
        List<WorkerGroup> groups = new ArrayList<>();

        // Shutdown hook for Ctrl+C
        Thread shutdownHook = new Thread(() -> running.set(false));
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // Collect unique paths needing each scan type (preserving insertion order for progress display)
        Set<Path> fileScanPaths = new LinkedHashSet<>();
        Set<Path> dirScanPaths  = new LinkedHashSet<>();
        for (WorkerSpec spec : workerSpecs) {
            if (spec.getType() == WorkerSpec.Type.READ
                    || spec.getType() == WorkerSpec.Type.READ_LISTING
                    || spec.getType() == WorkerSpec.Type.META_LISTING) {
                fileScanPaths.add(spec.getDirectory());
            }
            if (spec.getType() == WorkerSpec.Type.LISTING
                    || spec.getType() == WorkerSpec.Type.READ_LISTING
                    || spec.getType() == WorkerSpec.Type.META_LISTING) {
                dirScanPaths.add(spec.getDirectory());
            }
        }

        // Scan each unique path once, sharing the result across all workers targeting it
        Map<Path, List<Path>> fileCache = new HashMap<>();
        Map<Path, List<Path>> dirCache  = new HashMap<>();
        Set<Path> allScanPaths = new LinkedHashSet<>();
        allScanPaths.addAll(fileScanPaths);
        allScanPaths.addAll(dirScanPaths);
        int totalScans    = allScanPaths.size();
        int completedScans = 0;

        if (totalScans > 0) {
            System.out.printf("Initial directory listing in progress ... [0/%d completed]",
                    totalScans);
            System.out.flush();

            for (Path p : allScanPaths) {
                if (fileScanPaths.contains(p)) {
                    fileCache.put(p, Collections.unmodifiableList(scanFiles(p)));
                }
                if (dirScanPaths.contains(p)) {
                    List<Path> dirs = scanDirectories(p);
                    dirCache.put(p, Collections.unmodifiableList(dirs.isEmpty()
                            ? Collections.singletonList(p) : dirs));
                }
                System.out.printf("\rInitial directory listing in progress ... [%d/%d completed]",
                        ++completedScans, totalScans);
                System.out.flush();
            }
            System.out.printf("\rInitial directory listing complete.%s%n", " ".repeat(30));
        }

        // Timer starts only after all initial scans are done
        Instant start = Instant.now();

        // Start all worker groups — workers receive pre-computed lists, no per-thread scanning
        for (WorkerSpec spec : workerSpecs) {
            groups.add(new WorkerGroup(spec, metrics, corpusManager,
                    fileSizeMin, fileSizeMax, running, fileCache, dirCache));
        }

        // Schedule stop after duration
        ScheduledExecutorService stopper = Executors.newSingleThreadScheduledExecutor();
        stopper.schedule(() -> running.set(false), duration.toMillis(), TimeUnit.MILLISECONDS);

        // Dashboard refresh loop
        ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor();
        refresher.scheduleAtFixedRate(
                () -> dashboard.refresh(metrics, start, duration, groups, running.get()),
                0, DASHBOARD_REFRESH_MS, TimeUnit.MILLISECONDS);

        // Wait for stop
        while (running.get()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Signal workers and wait
        running.set(false);
        stopper.shutdownNow();
        refresher.shutdownNow();
        try {
            stopper.awaitTermination(5, TimeUnit.SECONDS);
            refresher.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (WorkerGroup g : groups) {
            try {
                g.awaitTermination();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Final dashboard render
        dashboard.refresh(metrics, start, duration, groups, false);
        dashboard.complete();

        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {}

        return PhaseResult.success("Test", Duration.between(start, Instant.now()));
    }

    private static List<Path> scanFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static List<Path> scanDirectories(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isDirectory).collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
