package com.iostresstest.worker;

import com.iostresstest.cli.WorkerSpec;
import com.iostresstest.corpus.CorpusManager;
import com.iostresstest.metrics.MetricsRegistry;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
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
                       long fileSizeMin, long fileSizeMax, AtomicBoolean running,
                       Map<Path, List<Path>> fileCache, Map<Path, List<Path>> dirCache) {
        this.spec = spec;
        this.executor = Executors.newFixedThreadPool(spec.getThreads(),
                r -> {
                    Thread t = new Thread(r, spec.getType().name().toLowerCase()
                            + "-worker-" + spec.getDirectory().getFileName());
                    t.setDaemon(true);
                    return t;
                });

        List<Runnable> workers = createWorkers(spec, metrics, corpusManager,
                fileSizeMin, fileSizeMax, running, fileCache, dirCache);
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
                                                 AtomicBoolean running,
                                                 Map<Path, List<Path>> fileCache,
                                                 Map<Path, List<Path>> dirCache) {
        Path dir = spec.getDirectory();
        List<Path> files = fileCache.getOrDefault(dir, Collections.emptyList());
        List<Path> dirs  = dirCache.getOrDefault(dir, Collections.singletonList(dir));

        List<Runnable> workers = new ArrayList<>(spec.getThreads());
        for (int i = 0; i < spec.getThreads(); i++) {
            switch (spec.getType()) {
                case READ:
                    workers.add(new ReadWorker(metrics, running, files));
                    break;
                case LISTING:
                    workers.add(new ListingWorker(metrics, running, dirs));
                    break;
                case WRITE:
                    workers.add(new WriteWorker(dir, fileSizeMin, fileSizeMax,
                            metrics, corpusManager, running));
                    break;
                case READ_LISTING:
                    workers.add(new ReadListingWorker(metrics, running,
                            spec.getReadRatioPct(), files, dirs));
                    break;
                case META_LISTING:
                    workers.add(new MetaListingWorker(metrics, running,
                            spec.getReadRatioPct(), spec.isFileOpen(), files, dirs));
                    break;
            }
        }
        return workers;
    }
}
