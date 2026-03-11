package com.iostresstest.worker;

import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Stresses the non-transfer portions of the IO subsystem by performing a mix of:
 * <ul>
 *   <li><b>Metadata operations</b>: reads file attributes (Basic, DOS, Owner, POSIX, ACL)
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
     * <p>Mirrors the operations in {@code FileAttributesHelper.appendFileAttributes}:
     * <ul>
     *   <li>BasicFileAttributeView - file times (modified, created, accessed)</li>
     *   <li>DosFileAttributeView - DOS attributes (if available)</li>
     *   <li>FileOwnerAttributeView - file owner</li>
     *   <li>PosixFileAttributeView - POSIX permissions, group (if available)</li>
     *   <li>AclFileAttributeView - ACL entries (if available)</li>
     * </ul>
     */
    private void fetchMetadata(Path file) {
        long start = System.nanoTime();
        try {
            // BasicFileAttributeView
            var basicView = Files.getFileAttributeView(file, BasicFileAttributeView.class);
            if (basicView != null) {
                basicView.readAttributes();
            }
            
            // DosFileAttributeView
            var dosView = Files.getFileAttributeView(file, DosFileAttributeView.class);
            if (dosView != null) {
                try {
                    dosView.readAttributes();
                } catch (IOException ignored) {
                    // May not be supported on all filesystems
                }
            }
            
            // FileOwnerAttributeView
            var ownerView = Files.getFileAttributeView(file, FileOwnerAttributeView.class);
            if (ownerView != null) {
                ownerView.getOwner();
            }
            
            // PosixFileAttributeView
            var posixView = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (posixView != null) {
                posixView.readAttributes();
            }
            
            // AclFileAttributeView
            AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (aclView != null) {
                aclView.getAcl();
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
