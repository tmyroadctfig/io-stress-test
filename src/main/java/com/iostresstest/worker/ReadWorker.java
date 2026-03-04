package com.iostresstest.worker;

import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Performs a mix of sequential full-file reads (50%) and random-seek chunk reads (50%).
 */
public class ReadWorker implements Runnable {

    private static final int SEQ_BUFFER_SIZE  = 64 * 1024;  // 64 KiB sequential buffer
    private static final int RAND_CHUNK_SIZE  = 64 * 1024;  // 64 KiB per seek
    private static final int RAND_SEEKS_PER_FILE = 8;       // seeks per random-read operation

    private final Path directory;
    private final MetricsRegistry metrics;
    private final AtomicBoolean running;
    private final Random rng = new Random();

    public ReadWorker(Path directory, MetricsRegistry metrics, AtomicBoolean running) {
        this.directory = directory;
        this.metrics   = metrics;
        this.running   = running;
    }

    @Override
    public void run() {
        List<Path> files = scanFiles(directory);
        if (files.isEmpty()) {
            metrics.recordError(OperationType.SEQ_READ);
            return;
        }

        ByteBuffer seqBuf  = ByteBuffer.allocate(SEQ_BUFFER_SIZE);
        ByteBuffer randBuf = ByteBuffer.allocate(RAND_CHUNK_SIZE);

        while (running.get()) {
            Path file = files.get(rng.nextInt(files.size()));
            if (rng.nextBoolean()) {
                sequentialRead(file, seqBuf);
            } else {
                randomSeekRead(file, randBuf);
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
            metrics.recordError(OperationType.SEQ_READ);
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
                long bytesRead = Math.max(0, n);
                metrics.record(OperationType.RAND_READ, System.nanoTime() - start, bytesRead);
            }
        } catch (IOException e) {
            metrics.recordError(OperationType.RAND_READ);
        }
    }

    private static List<Path> scanFiles(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}
