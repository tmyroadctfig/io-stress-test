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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ListingWorkerTest {

    private FileSystem fs;
    private Path listDir;

    @BeforeEach
    void setUp() throws IOException {
        fs = Jimfs.newFileSystem(Configuration.unix());
        listDir = fs.getPath("/listing");
        Files.createDirectories(listDir);
        Path sub1 = listDir.resolve("subA");
        Path sub2 = listDir.resolve("subB");
        Files.createDirectories(sub1);
        Files.createDirectories(sub2);
        Files.write(sub1.resolve("file1.txt"), "content1".getBytes());
        Files.write(sub2.resolve("file2.txt"), "content2".getBytes());
    }

    @AfterEach
    void tearDown() throws IOException {
        fs.close();
    }

    @Test
    void listingWorker_listsDirectoriesAndRecordsMetrics() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ListingWorker(listDir, metrics, running, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        assertFalse(t.isAlive(), "Worker thread should have stopped");
        Snapshot.TypeSnapshot snap = metrics.snapshot(System.nanoTime()).get(OperationType.DIR_LIST);
        assertTrue(snap.opCount > 0, "Expected at least one DIR_LIST operation");
    }

    @Test
    void listingWorker_recordsEntryCount() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ListingWorker(listDir, metrics, running, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        // DIR_LIST records entry count as "bytes"
        Snapshot.TypeSnapshot snap = metrics.snapshot(System.nanoTime()).get(OperationType.DIR_LIST);
        assertTrue(snap.byteCount > 0, "Expected listing entry counts to be recorded");
    }

    @Test
    void listingWorker_worksWithEmptyDirectory() throws IOException, InterruptedException {
        Path emptyDir = fs.getPath("/empty");
        Files.createDirectories(emptyDir);
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ListingWorker(emptyDir, metrics, running, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(200);
        running.set(false);
        t.join(2000);

        assertFalse(t.isAlive());
        assertTrue(metrics.snapshot(System.nanoTime()).get(OperationType.DIR_LIST).opCount > 0,
                "Listing worker should still record ops on an empty directory");
    }

    @Test
    void listingWorker_stopsWhenRunningFlagCleared() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new ListingWorker(listDir, metrics, running, new CountDownLatch(1)));
        t.setDaemon(true);
        t.start();
        Thread.sleep(100);
        running.set(false);
        t.join(2000);

        assertFalse(t.isAlive(), "Worker should stop after running flag is cleared");
    }
}
