package com.iostresstest.corpus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of synthetic files created for the test corpus and write-worker output.
 * All files registered here are deleted during the cleanup phase.
 * Existing files pointed to by workers are never touched.
 */
public class CorpusManager {

    private static final int WRITE_BUFFER_SIZE = 64 * 1024; // 64 KiB

    private final ConcurrentLinkedDeque<Path> managedFiles = new ConcurrentLinkedDeque<>();

    /**
     * Creates {@code fileCount} synthetic files under {@code directory} using a UUID-based
     * directory structure: {directory}/{uuid[0..2]}/{uuid[3..5]}/{uuid}.bin
     *
     * @param directory   root directory for the corpus
     * @param fileCount   number of files to create
     * @param minBytes    minimum file size in bytes
     * @param maxBytes    maximum file size in bytes
     * @param progressFn  called after each file is created with the file index (1-based)
     */
    public void createCorpus(Path directory, int fileCount, long minBytes, long maxBytes,
                             Consumer<Integer> progressFn) throws IOException {
        Files.createDirectories(directory);
        Random rng = new Random();
        byte[] buffer = new byte[WRITE_BUFFER_SIZE];

        for (int i = 0; i < fileCount; i++) {
            UUID uuid = UUID.randomUUID();
            Path file = resolveGuidPath(directory, uuid);
            Files.createDirectories(file.getParent());

            long fileSize = randomSize(rng, minBytes, maxBytes);
            writeRandomFile(file, fileSize, rng, buffer);
            managedFiles.add(file);

            progressFn.accept(i + 1);
        }
    }

    /**
     * Registers a file that was written by a WriteWorker so it is cleaned up later.
     */
    public void registerWrittenFile(Path file) {
        managedFiles.add(file);
    }

    /**
     * Deletes all managed files and attempts to remove empty parent directories.
     *
     * @param progressFn called after each file is deleted with (deletedCount, totalCount)
     */
    public void cleanup(Consumer<long[]> progressFn) {
        List<Path> files = new ArrayList<>(managedFiles);
        long total = files.size();
        long deleted = 0;

        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
                deleted++;
                tryDeleteEmptyParents(file.getParent(), 3);
            } catch (IOException e) {
                // Best-effort: log nothing, continue
            }
            progressFn.accept(new long[]{deleted, total});
        }
        managedFiles.clear();
    }

    public List<Path> getManagedFiles() {
        return Collections.unmodifiableList(new ArrayList<>(managedFiles));
    }

    /** Resolves the GUID-structured path for a UUID under the given base directory. */
    public static Path resolveGuidPath(Path base, UUID uuid) {
        String id = uuid.toString();
        String dir1 = id.substring(0, 3);
        String dir2 = id.substring(3, 6);
        return base.resolve(dir1).resolve(dir2).resolve(id + ".bin");
    }

    private static long randomSize(Random rng, long minBytes, long maxBytes) {
        if (minBytes >= maxBytes) return minBytes;
        return minBytes + (long) ((maxBytes - minBytes) * rng.nextDouble());
    }

    private static void writeRandomFile(Path file, long size, Random rng, byte[] buffer)
            throws IOException {
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

    private static void tryDeleteEmptyParents(Path dir, int levels) {
        for (int i = 0; i < levels && dir != null; i++, dir = dir.getParent()) {
            try {
                Files.deleteIfExists(dir);
            } catch (IOException e) {
                break; // directory not empty or no permission — stop
            }
        }
    }
}
