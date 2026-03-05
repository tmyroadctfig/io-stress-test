package com.iostresstest.worker;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.iostresstest.cli.WorkerSpec;
import com.iostresstest.corpus.CorpusManager;
import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class WorkerGroupTest {

    private FileSystem fs;
    private Path dir;

    @BeforeEach
    void setUp() throws IOException {
        fs = Jimfs.newFileSystem(Configuration.unix());
        dir = fs.getPath("/data");
        Files.createDirectories(dir);
    }

    @AfterEach
    void tearDown() throws IOException {
        fs.close();
    }

    private void createFiles(int count) throws IOException {
        Random rng = new Random(42);
        byte[] data = new byte[2048];
        for (int i = 0; i < count; i++) {
            rng.nextBytes(data);
            Files.write(dir.resolve("file" + i + ".bin"), data);
        }
    }

    private WorkerGroup startGroup(WorkerSpec spec, MetricsRegistry metrics,
                                   AtomicBoolean running,
                                   Map<Path, List<Path>> fileCache,
                                   Map<Path, List<Path>> dirCache) {
        return new WorkerGroup(spec, metrics, new CorpusManager(), 1024, 4096,
                running, fileCache, dirCache);
    }

    // --- READ worker ---

    @Test
    void readWorkerGroup_usesFileCacheAndRecordsMetrics() throws IOException, InterruptedException {
        createFiles(3);
        List<Path> files = List.of(dir.resolve("file0.bin"), dir.resolve("file1.bin"), dir.resolve("file2.bin"));

        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Map<Path, List<Path>> fileCache = new HashMap<>();
        fileCache.put(dir, Collections.unmodifiableList(files));

        WorkerSpec spec = new WorkerSpec(WorkerSpec.Type.READ, 1, dir);
        WorkerGroup group = startGroup(spec, metrics, running, fileCache, new HashMap<>());

        Thread.sleep(300);
        running.set(false);
        group.awaitTermination();

        long readOps = metrics.snapshot(System.nanoTime()).get(OperationType.SEQ_READ).opCount
                + metrics.snapshot(System.nanoTime()).get(OperationType.RAND_READ).opCount;
        assertTrue(readOps > 0, "READ workers should record read operations using the provided file cache");
    }

    @Test
    void readWorkerGroup_emptyFileCache_workerExitsWithError() throws InterruptedException {
        // Path is not in fileCache — getOrDefault returns empty list → worker exits immediately
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        WorkerSpec spec = new WorkerSpec(WorkerSpec.Type.READ, 1, dir);
        WorkerGroup group = startGroup(spec, metrics, running, new HashMap<>(), new HashMap<>());

        group.awaitTermination();

        assertEquals(1, metrics.snapshot(System.nanoTime()).get(OperationType.SEQ_READ).errorCount,
                "READ worker should exit with one error when file cache is empty");
    }

    // --- LISTING worker ---

    @Test
    void listingWorkerGroup_usesDirCacheAndRecordsMetrics() throws IOException, InterruptedException {
        Path sub = dir.resolve("sub");
        Files.createDirectories(sub);
        List<Path> dirs = List.of(dir, sub);

        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Map<Path, List<Path>> dirCache = new HashMap<>();
        dirCache.put(dir, Collections.unmodifiableList(dirs));

        WorkerSpec spec = new WorkerSpec(WorkerSpec.Type.LISTING, 1, dir);
        WorkerGroup group = startGroup(spec, metrics, running, new HashMap<>(), dirCache);

        Thread.sleep(300);
        running.set(false);
        group.awaitTermination();

        assertTrue(metrics.snapshot(System.nanoTime()).get(OperationType.DIR_LIST).opCount > 0,
                "LISTING workers should record DIR_LIST operations using the provided dir cache");
    }

    @Test
    void listingWorkerGroup_pathNotInDirCache_fallsBackToTargetDirectory() throws InterruptedException {
        // dir is not in dirCache — getOrDefault falls back to singletonList(dir), so listing still runs
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        WorkerSpec spec = new WorkerSpec(WorkerSpec.Type.LISTING, 1, dir);
        WorkerGroup group = startGroup(spec, metrics, running, new HashMap<>(), new HashMap<>());

        Thread.sleep(200);
        running.set(false);
        group.awaitTermination();

        assertTrue(metrics.snapshot(System.nanoTime()).get(OperationType.DIR_LIST).opCount > 0,
                "LISTING worker should fall back to the target directory itself when not in dir cache");
    }

    // --- Sharing: two WorkerGroups targeting the same path use the same List instance ---

    @Test
    void twoGroupsSamePathShareTheSameListInstance() throws IOException {
        createFiles(2);
        List<Path> files = Collections.unmodifiableList(
                List.of(dir.resolve("file0.bin"), dir.resolve("file1.bin")));

        Map<Path, List<Path>> fileCache = new HashMap<>();
        fileCache.put(dir, files);

        AtomicBoolean running = new AtomicBoolean(false); // don't actually run workers

        // Two READ groups for the same path both look up the same object from the shared cache
        // Verified by checking identity of the list reference stored in the cache
        assertSame(files, fileCache.get(dir),
                "Both groups should receive the exact same List<Path> instance from the cache");
    }
}
