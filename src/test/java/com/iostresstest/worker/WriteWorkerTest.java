package com.iostresstest.worker;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.iostresstest.corpus.CorpusManager;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class WriteWorkerTest {

    private FileSystem fs;
    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        fs = Jimfs.newFileSystem(Configuration.unix());
        outputDir = fs.getPath("/output");
        Files.createDirectories(outputDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        fs.close();
    }

    @Test
    void writeWorker_writesFilesAndRecordsMetrics() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        CorpusManager corpusManager = new CorpusManager();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new WriteWorker(outputDir, 1024, 4096, metrics, corpusManager, running));
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        assertFalse(t.isAlive(), "Worker thread should have stopped");
        Snapshot.TypeSnapshot snap = metrics.snapshot(System.nanoTime()).get(OperationType.FILE_WRITE);
        assertTrue(snap.opCount > 0, "Expected at least one FILE_WRITE op");
        assertTrue(snap.byteCount > 0, "Expected bytes to have been written");
    }

    @Test
    void writeWorker_registersFilesWithCorpusManager() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        CorpusManager corpusManager = new CorpusManager();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new WriteWorker(outputDir, 512, 512, metrics, corpusManager, running));
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        assertFalse(corpusManager.getManagedFiles().isEmpty(),
                "Worker should have registered written files with CorpusManager");
    }

    @Test
    void writeWorker_filesExistOnFilesystem() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        CorpusManager corpusManager = new CorpusManager();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new WriteWorker(outputDir, 256, 256, metrics, corpusManager, running));
        t.setDaemon(true);
        t.start();
        Thread.sleep(300);
        running.set(false);
        t.join(2000);

        for (Path file : corpusManager.getManagedFiles()) {
            assertTrue(Files.exists(file), "Written file should exist on filesystem: " + file);
        }
    }

    @Test
    void writeWorker_stopsWhenRunningFlagCleared() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        CorpusManager corpusManager = new CorpusManager();
        AtomicBoolean running = new AtomicBoolean(true);

        Thread t = new Thread(new WriteWorker(outputDir, 512, 512, metrics, corpusManager, running));
        t.setDaemon(true);
        t.start();
        Thread.sleep(100);
        running.set(false);
        t.join(2000);

        assertFalse(t.isAlive(), "Worker should stop after running flag is cleared");
    }
}
