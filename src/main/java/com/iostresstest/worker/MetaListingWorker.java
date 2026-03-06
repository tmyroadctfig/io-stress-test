package com.iostresstest.worker;

import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Stresses the non-transfer portions of the IO subsystem by performing a mix of:
 * <ul>
 *   <li><b>Metadata operations</b>: reads file attributes (POSIX on Linux/macOS, ACL on Windows)
 *       and, if enabled, opens and immediately closes the file via {@link Files#newByteChannel}.
 *   <li><b>Directory listings</b>: calls {@link Files#list} on a randomly chosen directory,
 *       identical to {@link ListingWorker}.
 * </ul>
 *
 * <p>The ratio of metadata-to-listing operations is configurable via the {@code [N]} bracket
 * syntax, e.g. {@code metadata+list[75]:8:/mnt/data} (75% metadata, 25% listing).
 * File open/close can be disabled with {@code metadata+list[50,noopen]}.
 */
public class MetaListingWorker implements Runnable {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().startsWith("win");

    private final MetricsRegistry metrics;
    private final AtomicBoolean running;
    private final int metaRatioPct;
    private final boolean fileOpen;
    private final List<Path> files;
    private final List<Path> dirs;
    private final Random rng = new Random();

    public MetaListingWorker(MetricsRegistry metrics, AtomicBoolean running,
                             int metaRatioPct, boolean fileOpen,
                             List<Path> files, List<Path> dirs) {
        this.metrics      = metrics;
        this.running      = running;
        this.metaRatioPct = metaRatioPct;
        this.fileOpen     = fileOpen;
        this.files        = files;
        this.dirs         = dirs;
    }

    @Override
    public void run() {
        if (files.isEmpty()) {
            metrics.recordError(OperationType.FILE_META);
            return;
        }

        while (running.get()) {
            if (rng.nextInt(100) < metaRatioPct) {
                fetchMetadata(files.get(rng.nextInt(files.size())));
            } else {
                listDirectory(dirs.get(rng.nextInt(dirs.size())));
            }
        }
    }

    /**
     * Fetches file attributes and optionally opens/closes the file, recording the combined
     * elapsed time as a single {@link OperationType#FILE_META} operation.
     *
     * <p>On Windows: reads ACL entries and owner via {@link AclFileAttributeView}.
     * On Linux/macOS: reads permissions, owner, and group via POSIX attributes.
     */
    private void fetchMetadata(Path file) {
        long start = System.nanoTime();
        try {
            Files.readAttributes(file, BasicFileAttributes.class);
            if (IS_WINDOWS) {
                AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
                view.getAcl();
                view.getOwner();
            } else {
                Files.readAttributes(file, PosixFileAttributes.class);
            }

            if (fileOpen) {
                try (SeekableByteChannel ch = Files.newByteChannel(file)) {
                    // open and immediately close without reading any data
                }
            }

            metrics.record(OperationType.FILE_META, System.nanoTime() - start, 0);
        } catch (IOException e) {
            metrics.recordError(OperationType.FILE_META, e);
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
