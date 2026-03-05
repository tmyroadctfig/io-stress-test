package com.iostresstest.phase;

import com.iostresstest.cli.WorkerSpec;
import com.iostresstest.corpus.CorpusManager;
import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.ui.AnsiDashboard;
import com.iostresstest.worker.WorkerGroup;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

        // Count workers that perform an initial directory scan (READ, LISTING, READ_LISTING)
        int scanWorkerCount = workerSpecs.stream()
                .filter(s -> s.getType() != WorkerSpec.Type.WRITE)
                .mapToInt(WorkerSpec::getThreads)
                .sum();
        CountDownLatch readyLatch = new CountDownLatch(scanWorkerCount);

        // Start all worker groups
        for (WorkerSpec spec : workerSpecs) {
            groups.add(new WorkerGroup(spec, metrics, corpusManager,
                    fileSizeMin, fileSizeMax, running, readyLatch));
        }

        // Wait for all initial directory scans to finish before starting the timed run
        if (scanWorkerCount > 0) {
            System.out.printf("Initial directory listing in progress ... [0/%d completed]",
                    scanWorkerCount);
            System.out.flush();
            try {
                while (!readyLatch.await(100, TimeUnit.MILLISECONDS)) {
                    int completed = scanWorkerCount - (int) readyLatch.getCount();
                    System.out.printf("\rInitial directory listing in progress ... [%d/%d completed]",
                            completed, scanWorkerCount);
                    System.out.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.printf("\rInitial directory listing complete.%s%n", " ".repeat(30));
        }

        // Timer starts only after all initial scans are done
        Instant start = Instant.now();

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
}
