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

class ReadWorkerTest {

    private FileSystem fs;
    private Path readDir;

    @BeforeEach
    void setUp() throws IOException {
        fs = Jimfs.newFileSystem(Configuration.unix());
        readDir = fs.getPath("/data");
        Files.createDirectories(readDir);
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
            Files.write(readDir.resolve("file" + i + ".bin"), data);
        }
    }

    @Test
    void readWorker_readsFilesAndRecordsMetrics() throws IOException, InterruptedException {
        createFiles(5, 8192);
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ReadWorker(readDir, metrics, running, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(400);
        running.set(false);
        t.join(2000);

        assertFalse(t.isAlive(), "Worker thread should have stopped");
        Snapshot snap = metrics.snapshot(System.nanoTime());
        long totalOps = snap.get(OperationType.SEQ_READ).opCount
                + snap.get(OperationType.RAND_READ).opCount;
        assertTrue(totalOps > 0, "Expected at least one read operation");
    }

    @Test
    void readWorker_recordsBytesRead() throws IOException, InterruptedException {
        createFiles(3, 4096);
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ReadWorker(readDir, metrics, running, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        Snapshot snap = metrics.snapshot(System.nanoTime());
        long totalBytes = snap.get(OperationType.SEQ_READ).byteCount
                + snap.get(OperationType.RAND_READ).byteCount;
        assertTrue(totalBytes > 0, "Expected bytes to have been read");
    }

    @Test
    void readWorker_recordsError_whenDirectoryEmpty() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ReadWorker(readDir, metrics, running, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        t.join(2000);

        assertFalse(t.isAlive(), "Worker should exit immediately when no files found");
        assertEquals(1, metrics.snapshot(System.nanoTime()).get(OperationType.SEQ_READ).errorCount,
                "Expected one error for empty directory");
    }

    @Test
    void readWorker_stopsWhenRunningFlagCleared() throws IOException, InterruptedException {
        createFiles(3, 2048);
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ReadWorker(readDir, metrics, running, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(100);
        running.set(false);
        t.join(2000);

        assertFalse(t.isAlive(), "Worker should stop after running flag is cleared");
    }
}
