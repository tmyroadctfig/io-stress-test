package com.iostresstest.corpus;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorpusManagerTest {

    private FileSystem fs;
    private Path root;

    @BeforeEach
    void setUp() throws IOException {
        fs = Jimfs.newFileSystem(Configuration.unix());
        root = fs.getPath("/corpus");
        Files.createDirectories(root);
    }

    @AfterEach
    void tearDown() throws IOException {
        fs.close();
    }

    @Test
    void resolveGuidPath_returnsCorrectStructure() {
        UUID uuid = UUID.fromString("abcdef12-3456-7890-abcd-ef1234567890");
        Path result = CorpusManager.resolveGuidPath(root, uuid);
        assertEquals(
                root.resolve("abc").resolve("def").resolve("abcdef12-3456-7890-abcd-ef1234567890.bin"),
                result);
    }

    @Test
    void createCorpus_createsExpectedNumberOfFiles() throws IOException {
        CorpusManager manager = new CorpusManager();
        manager.createCorpus(root, 5, 1024, 4096, i -> {});

        assertEquals(5, manager.getManagedFiles().size());
        for (Path file : manager.getManagedFiles()) {
            assertTrue(Files.exists(file), "Expected file to exist: " + file);
            assertTrue(Files.size(file) >= 1024, "Expected file >= 1024 bytes");
            assertTrue(Files.size(file) <= 4096, "Expected file <= 4096 bytes");
        }
    }

    @Test
    void createCorpus_invokesProgressCallbackForEachFile() throws IOException {
        CorpusManager manager = new CorpusManager();
        List<Integer> progress = new ArrayList<>();
        manager.createCorpus(root, 3, 512, 512, progress::add);
        assertEquals(List.of(1, 2, 3), progress);
    }

    @Test
    void createCorpus_filesUseGuidStructure() throws IOException {
        CorpusManager manager = new CorpusManager();
        manager.createCorpus(root, 2, 256, 256, i -> {});

        for (Path file : manager.getManagedFiles()) {
            // Path should be: /corpus/<3chars>/<3chars>/<uuid>.bin
            assertEquals(4, file.getNameCount(), "Expected 4 path components: corpus/dir1/dir2/file.bin");
            assertTrue(file.getFileName().toString().endsWith(".bin"));
        }
    }

    @Test
    void cleanup_deletesAllManagedFiles() throws IOException {
        CorpusManager manager = new CorpusManager();
        manager.createCorpus(root, 3, 512, 512, i -> {});
        List<Path> files = new ArrayList<>(manager.getManagedFiles());
        assertFalse(files.isEmpty());

        manager.cleanup(arr -> {});

        for (Path file : files) {
            assertFalse(Files.exists(file), "Expected file to be deleted: " + file);
        }
        assertTrue(manager.getManagedFiles().isEmpty());
    }

    @Test
    void cleanup_progressCallbackReceivesCorrectCounts() throws IOException {
        CorpusManager manager = new CorpusManager();
        manager.createCorpus(root, 4, 256, 256, i -> {});
        List<long[]> calls = new ArrayList<>();
        manager.cleanup(arr -> calls.add(arr.clone()));

        assertEquals(4, calls.size());
        assertEquals(4L, calls.get(3)[0]); // deleted count at end
        assertEquals(4L, calls.get(3)[1]); // total count
    }

    @Test
    void registerWrittenFile_tracksFileForCleanup() throws IOException {
        CorpusManager manager = new CorpusManager();
        Path file = root.resolve("test.bin");
        Files.write(file, new byte[]{1, 2, 3});
        manager.registerWrittenFile(file);
        assertTrue(manager.getManagedFiles().contains(file));
    }

    @Test
    void cleanup_clearsRegisteredFilesAfterDeletion() throws IOException {
        CorpusManager manager = new CorpusManager();
        Path file = root.resolve("test.bin");
        Files.write(file, new byte[]{1, 2, 3});
        manager.registerWrittenFile(file);

        manager.cleanup(arr -> {});

        assertTrue(manager.getManagedFiles().isEmpty());
        assertFalse(Files.exists(file));
    }
}
