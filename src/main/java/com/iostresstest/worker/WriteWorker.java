package com.iostresstest.worker;

import com.iostresstest.corpus.CorpusManager;
import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes random-sized binary files using a UUID-based directory structure:
 *   {base}/{uuid[0..2]}/{uuid[3..5]}/{uuid}.bin
 * All written files are registered with CorpusManager for cleanup.
 */
public class WriteWorker implements Runnable {

    private static final int WRITE_BUFFER_SIZE = 64 * 1024;

    private final Path directory;
    private final long fileSizeMin;
    private final long fileSizeMax;
    private final MetricsRegistry metrics;
    private final CorpusManager corpusManager;
    private final AtomicBoolean running;
    private final Random rng = new Random();

    public WriteWorker(Path directory, long fileSizeMin, long fileSizeMax,
                       MetricsRegistry metrics, CorpusManager corpusManager,
                       AtomicBoolean running) {
        this.directory     = directory;
        this.fileSizeMin   = fileSizeMin;
        this.fileSizeMax   = fileSizeMax;
        this.metrics       = metrics;
        this.corpusManager = corpusManager;
        this.running       = running;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[WRITE_BUFFER_SIZE];

        while (running.get()) {
            UUID uuid     = UUID.randomUUID();
            Path file     = CorpusManager.resolveGuidPath(directory, uuid);
            long fileSize = randomSize();

            long start = System.nanoTime();
            try {
                Files.createDirectories(file.getParent());
                writeFile(file, fileSize, buffer);
                corpusManager.registerWrittenFile(file);
                metrics.record(OperationType.FILE_WRITE, System.nanoTime() - start, fileSize);
            } catch (IOException e) {
                metrics.recordError(OperationType.FILE_WRITE, e);
            }
        }
    }

    private void writeFile(Path file, long size, byte[] buffer) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            long remaining = size;
            while (remaining > 0) {
                int chunk = (int) Math.min(buffer.length, remaining);
                rng.nextBytes(buffer);
                out.write(buffer, 0, chunk);
                remaining -= chunk;
            }
        }
    }

    private long randomSize() {
        if (fileSizeMin >= fileSizeMax) return fileSizeMin;
        return fileSizeMin + (long) ((fileSizeMax - fileSizeMin) * rng.nextDouble());
    }
}
