package com.iostresstest.corpus;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanaryManagerTest {

    private FileSystem fs;
    private Path dir1;
    private Path dir2;

    @BeforeEach
    void setUp() throws IOException {
        fs = Jimfs.newFileSystem(Configuration.unix());
        dir1 = fs.getPath("/dir1");
        dir2 = fs.getPath("/dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);
    }

    @AfterEach
    void tearDown() throws IOException {
        fs.close();
    }

    @Test
    void hasCanaries_returnsFalse_beforeDrop() {
        assertFalse(new CanaryManager().hasCanaries());
    }

    @Test
    void hasCanaries_returnsTrue_afterDrop() throws IOException {
        CanaryManager manager = new CanaryManager();
        manager.drop(List.of(dir1));
        assertTrue(manager.hasCanaries());
    }

    @Test
    void drop_createsCanaryFileInEachDirectory() throws IOException {
        CanaryManager manager = new CanaryManager();
        manager.drop(List.of(dir1, dir2));
        assertTrue(Files.exists(dir1.resolve(CanaryManager.CANARY_FILENAME)));
        assertTrue(Files.exists(dir2.resolve(CanaryManager.CANARY_FILENAME)));
    }

    @Test
    void drop_canaryFileHasEicarSize() throws IOException {
        CanaryManager manager = new CanaryManager();
        manager.drop(List.of(dir1));
        Path canary = dir1.resolve(CanaryManager.CANARY_FILENAME);
        assertEquals(68L, Files.size(canary), "EICAR test string is exactly 68 bytes");
    }

    @Test
    void verify_returnsOk_whenFileUnchanged() throws IOException {
        CanaryManager manager = new CanaryManager();
        manager.drop(List.of(dir1));
        List<CanaryResult> results = manager.verify();
        assertEquals(1, results.size());
        assertEquals(CanaryResult.Status.OK, results.get(0).getStatus());
        assertTrue(results.get(0).isOk());
    }

    @Test
    void verify_returnsMissing_whenFileDeleted() throws IOException {
        CanaryManager manager = new CanaryManager();
        manager.drop(List.of(dir1));
        Files.delete(dir1.resolve(CanaryManager.CANARY_FILENAME));

        List<CanaryResult> results = manager.verify();
        assertEquals(1, results.size());
        assertEquals(CanaryResult.Status.MISSING, results.get(0).getStatus());
        assertFalse(results.get(0).isOk());
    }

    @Test
    void verify_returnsAltered_whenFileContentReplaced() throws IOException {
        CanaryManager manager = new CanaryManager();
        manager.drop(List.of(dir1));
        Path canary = dir1.resolve(CanaryManager.CANARY_FILENAME);
        Files.write(canary, "This file has been quarantined by antivirus software.".getBytes(StandardCharsets.UTF_8));

        List<CanaryResult> results = manager.verify();
        assertEquals(1, results.size());
        assertEquals(CanaryResult.Status.ALTERED, results.get(0).getStatus());
        assertNotNull(results.get(0).getDetail());
        assertTrue(results.get(0).getDetail().contains("quarantined"));
    }

    @Test
    void verify_multipleDirectories_oneOkOneMissing() throws IOException {
        CanaryManager manager = new CanaryManager();
        manager.drop(List.of(dir1, dir2));
        Files.delete(dir2.resolve(CanaryManager.CANARY_FILENAME));

        List<CanaryResult> results = manager.verify();
        assertEquals(2, results.size());

        long okCount = results.stream().filter(CanaryResult::isOk).count();
        long missingCount = results.stream()
                .filter(r -> r.getStatus() == CanaryResult.Status.MISSING).count();
        assertEquals(1, okCount);
        assertEquals(1, missingCount);
    }

    @Test
    void cleanup_deletesAllCanaryFiles() throws IOException {
        CanaryManager manager = new CanaryManager();
        manager.drop(List.of(dir1, dir2));
        manager.cleanup();
        assertFalse(Files.exists(dir1.resolve(CanaryManager.CANARY_FILENAME)));
        assertFalse(Files.exists(dir2.resolve(CanaryManager.CANARY_FILENAME)));
        assertFalse(manager.hasCanaries());
    }

    @Test
    void verify_filePathPointsToCorrectLocation() throws IOException {
        CanaryManager manager = new CanaryManager();
        manager.drop(List.of(dir1));
        List<CanaryResult> results = manager.verify();
        assertEquals(dir1.resolve(CanaryManager.CANARY_FILENAME), results.get(0).getFile());
    }
}
