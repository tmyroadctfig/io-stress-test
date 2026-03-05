package com.iostresstest.worker;

import com.iostresstest.cli.WorkerSpec;
import com.iostresstest.corpus.CorpusManager;
import com.iostresstest.metrics.MetricsRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a pool of worker threads for a single WorkerSpec.
 */
public class WorkerGroup {

    private final WorkerSpec spec;
    private final ExecutorService executor;

    public WorkerGroup(WorkerSpec spec, MetricsRegistry metrics, CorpusManager corpusManager,
                       long fileSizeMin, long fileSizeMax, AtomicBoolean running) {
        this.spec = spec;
        this.executor = Executors.newFixedThreadPool(spec.getThreads(),
                r -> {
                    Thread t = new Thread(r, spec.getType().name().toLowerCase()
                            + "-worker-" + spec.getDirectory().getFileName());
                    t.setDaemon(true);
                    return t;
                });

        List<Runnable> workers = createWorkers(spec, metrics, corpusManager,
                fileSizeMin, fileSizeMax, running);
        workers.forEach(executor::submit);
    }

    public WorkerSpec getSpec() {
        return spec;
    }

    public void awaitTermination() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    private static List<Runnable> createWorkers(WorkerSpec spec, MetricsRegistry metrics,
                                                 CorpusManager corpusManager,
                                                 long fileSizeMin, long fileSizeMax,
                                                 AtomicBoolean running) {
        List<Runnable> workers = new ArrayList<>(spec.getThreads());
        for (int i = 0; i < spec.getThreads(); i++) {
            switch (spec.getType()) {
                case READ:
                    workers.add(new ReadWorker(spec.getDirectory(), metrics, running));
                    break;
                case LISTING:
                    workers.add(new ListingWorker(spec.getDirectory(), metrics, running));
                    break;
                case WRITE:
                    workers.add(new WriteWorker(spec.getDirectory(), fileSizeMin, fileSizeMax,
                            metrics, corpusManager, running));
                    break;
                case READ_LISTING:
                    workers.add(new ReadListingWorker(spec.getDirectory(), metrics,
                            running, spec.getReadRatioPct()));
                    break;
            }
        }
        return workers;
    }
}
