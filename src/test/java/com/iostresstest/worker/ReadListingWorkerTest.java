package com.iostresstest.worker;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;
import com.iostresstest.metrics.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ReadListingWorkerTest {

    private FileSystem fs;
    private Path dataDir;

    @BeforeEach
    void setUp() throws IOException {
        fs = Jimfs.newFileSystem(Configuration.unix());
        dataDir = fs.getPath("/data");
        Files.createDirectories(dataDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        fs.close();
    }

    private void createFiles(int count, int sizeBytes) throws IOException {
        Random rng = new Random(42);
        byte[] data = new byte[sizeBytes];
        for (int i = 0; i < count; i++) {
            rng.nextBytes(data);
            Files.write(dataDir.resolve("file" + i + ".bin"), data);
        }
    }

    @Test
    void worker_recordsBothReadsAndListings() throws IOException, InterruptedException {
        createFiles(5, 4096);
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ReadListingWorker(dataDir, metrics, running, 50, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(500);
        running.set(false);
        t.join(2000);

        assertFalse(t.isAlive());
        Snapshot snap = metrics.snapshot(System.nanoTime());
        long readOps = snap.get(OperationType.SEQ_READ).opCount
                     + snap.get(OperationType.RAND_READ).opCount;
        long listOps = snap.get(OperationType.DIR_LIST).opCount;
        assertTrue(readOps > 0, "Expected read operations");
        assertTrue(listOps > 0, "Expected listing operations");
    }

    @Test
    void worker_withReadRatio100_onlyPerformsReads() throws IOException, InterruptedException {
        createFiles(3, 2048);
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ReadListingWorker(dataDir, metrics, running, 100, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        Snapshot snap = metrics.snapshot(System.nanoTime());
        long readOps = snap.get(OperationType.SEQ_READ).opCount
                     + snap.get(OperationType.RAND_READ).opCount;
        assertTrue(readOps > 0, "Expected read operations with ratio=100");
        assertEquals(0, snap.get(OperationType.DIR_LIST).opCount,
                "Expected no listings with ratio=100");
    }

    @Test
    void worker_withReadRatio0_onlyPerformsListings() throws IOException, InterruptedException {
        createFiles(3, 2048);
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ReadListingWorker(dataDir, metrics, running, 0, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        Snapshot snap = metrics.snapshot(System.nanoTime());
        long readOps = snap.get(OperationType.SEQ_READ).opCount
                     + snap.get(OperationType.RAND_READ).opCount;
        assertEquals(0, readOps, "Expected no reads with ratio=0");
        assertTrue(snap.get(OperationType.DIR_LIST).opCount > 0,
                "Expected listing operations with ratio=0");
    }

    @Test
    void worker_recordsError_whenDirectoryEmpty() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ReadListingWorker(dataDir, metrics, running, 50, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        t.join(2000);

        assertFalse(t.isAlive(), "Worker should exit immediately when no files found");
        assertEquals(1, metrics.snapshot(System.nanoTime()).get(OperationType.SEQ_READ).errorCount);
    }

    @Test
    void worker_stopsWhenRunningFlagCleared() throws IOException, InterruptedException {
        createFiles(3, 2048);
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ReadListingWorker(dataDir, metrics, running, 50, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(100);
        running.set(false);
        t.join(2000);

        assertFalse(t.isAlive(), "Worker should stop after running flag is cleared");
    }
}
