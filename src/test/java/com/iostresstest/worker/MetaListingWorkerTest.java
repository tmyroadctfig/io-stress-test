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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MetaListingWorkerTest {

    private FileSystem fs;
    private Path testDir;
    private List<Path> files;
    private List<Path> dirs;

    @BeforeEach
    void setUp() throws IOException {
        // Arrange
        fs = Jimfs.newFileSystem(Configuration.unix());
        testDir = fs.getPath("/test");
        Files.createDirectories(testDir);
        
        Path file1 = testDir.resolve("file1.txt");
        Path file2 = testDir.resolve("file2.txt");
        Files.write(file1, "content1".getBytes());
        Files.write(file2, "content2".getBytes());
        
        files = List.of(file1, file2);
        dirs = List.of(testDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        fs.close();
    }

    @Test
    void run_WithMetadataRatio100_RecordsFileMetaOperations() throws InterruptedException {
        // Arrange
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);
        MetaListingWorker worker = new MetaListingWorker(metrics, running, 100, false, files, dirs);

        // Act
        Thread t = new Thread(worker);
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        // Assert
        assertFalse(t.isAlive());
        Snapshot.TypeSnapshot snap = metrics.snapshot(System.nanoTime()).get(OperationType.FILE_META);
        assertTrue(snap.opCount > 0, "Expected FILE_META operations");
    }

    @Test
    void run_WithMetadataRatio0_RecordsDirListOperations() throws InterruptedException {
        // Arrange
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);
        MetaListingWorker worker = new MetaListingWorker(metrics, running, 0, false, files, dirs);

        // Act
        Thread t = new Thread(worker);
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        // Assert
        assertFalse(t.isAlive());
        Snapshot.TypeSnapshot snap = metrics.snapshot(System.nanoTime()).get(OperationType.DIR_LIST);
        assertTrue(snap.opCount > 0, "Expected DIR_LIST operations");
    }

    @Test
    void run_WithMixedRatio_RecordsBothOperationTypes() throws InterruptedException {
        // Arrange
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);
        MetaListingWorker worker = new MetaListingWorker(metrics, running, 50, false, files, dirs);

        // Act
        Thread t = new Thread(worker);
        t.setDaemon(true);
        t.start();
        Thread.sleep(500);
        running.set(false);
        t.join(2000);

        // Assert
        assertFalse(t.isAlive());
        var snapshot = metrics.snapshot(System.nanoTime());
        assertTrue(snapshot.get(OperationType.FILE_META).opCount > 0, "Expected FILE_META operations");
        assertTrue(snapshot.get(OperationType.DIR_LIST).opCount > 0, "Expected DIR_LIST operations");
    }

    @Test
    void run_WithFileOpenEnabled_CompletesSuccessfully() throws InterruptedException {
        // Arrange
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);
        MetaListingWorker worker = new MetaListingWorker(metrics, running, 100, true, files, dirs);

        // Act
        Thread t = new Thread(worker);
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        // Assert
        assertFalse(t.isAlive());
        Snapshot.TypeSnapshot snap = metrics.snapshot(System.nanoTime()).get(OperationType.FILE_META);
        assertTrue(snap.opCount > 0, "Expected FILE_META operations with file open");
    }

    @Test
    void run_StopsWhenRunningFlagCleared() throws InterruptedException {
        // Arrange
        MetricsRegistry metrics = new MetricsRegistry();
        AtomicBoolean running = new AtomicBoolean(true);
        MetaListingWorker worker = new MetaListingWorker(metrics, running, 50, false, files, dirs);

        // Act
        Thread t = new Thread(worker);
        t.setDaemon(true);
        t.start();
        Thread.sleep(100);
        running.set(false);
        t.join(2000);

        // Assert
        assertFalse(t.isAlive(), "Worker should stop after running flag is cleared");
    }
}
