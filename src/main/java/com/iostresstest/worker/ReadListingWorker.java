package com.iostresstest.worker;

import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Interleaves sequential/random reads with directory listings in a single worker thread.
 * On each iteration the worker randomly chooses to perform a read or a listing based on
 * the configured read ratio (e.g. 75 means 75% reads, 25% listings).
 */
public class ReadListingWorker implements Runnable {

    private static final int SEQ_BUFFER_SIZE     = 64 * 1024;
    private static final int RAND_CHUNK_SIZE     = 64 * 1024;
    private static final int RAND_SEEKS_PER_FILE = 8;

    private final MetricsRegistry metrics;
    private final AtomicBoolean running;
    private final int readRatioPct;
    private final List<Path> files;
    private final List<Path> dirs;
    private final Random rng = new Random();

    public ReadListingWorker(MetricsRegistry metrics, AtomicBoolean running,
                             int readRatioPct, List<Path> files, List<Path> dirs) {
        this.metrics      = metrics;
        this.running      = running;
        this.readRatioPct = readRatioPct;
        this.files        = files;
        this.dirs         = dirs;
    }

    @Override
    public void run() {
        if (files.isEmpty()) {
            metrics.recordError(OperationType.SEQ_READ);
            return;
        }

        ByteBuffer seqBuf  = ByteBuffer.allocate(SEQ_BUFFER_SIZE);
        ByteBuffer randBuf = ByteBuffer.allocate(RAND_CHUNK_SIZE);

        while (running.get()) {
            if (rng.nextInt(100) < readRatioPct) {
                Path file = files.get(rng.nextInt(files.size()));  // lists are immutable, safe to read
                if (rng.nextBoolean()) {
                    sequentialRead(file, seqBuf);
                } else {
                    randomSeekRead(file, randBuf);
                }
            } else {
                Path dir = dirs.get(rng.nextInt(dirs.size()));
                listDirectory(dir);
            }
        }
    }

    private void sequentialRead(Path file, ByteBuffer buf) {
        long start = System.nanoTime();
        long bytesRead = 0;
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            buf.clear();
            int n;
            while ((n = ch.read(buf)) > 0) {
                bytesRead += n;
                buf.clear();
            }
            metrics.record(OperationType.SEQ_READ, System.nanoTime() - start, bytesRead);
        } catch (IOException e) {
            metrics.recordError(OperationType.SEQ_READ, e);
        }
    }

    private void randomSeekRead(Path file, ByteBuffer buf) {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = ch.size();
            if (fileSize == 0) return;
            for (int i = 0; i < RAND_SEEKS_PER_FILE && running.get(); i++) {
                long pos = (long) (rng.nextDouble() * fileSize);
                long start = System.nanoTime();
                buf.clear();
                int n = ch.read(buf, pos);
                metrics.record(OperationType.RAND_READ, System.nanoTime() - start, Math.max(0, n));
            }
        } catch (IOException e) {
            metrics.recordError(OperationType.RAND_READ, e);
        }
    }

    private void listDirectory(Path dir) {
        long start = System.nanoTime();
        try (Stream<Path> entries = Files.list(dir)) {
            long count = entries.count();
            metrics.record(OperationType.DIR_LIST, System.nanoTime() - start, count);
        } catch (IOException e) {
            metrics.recordError(OperationType.DIR_LIST, e);
        }
    }

}
