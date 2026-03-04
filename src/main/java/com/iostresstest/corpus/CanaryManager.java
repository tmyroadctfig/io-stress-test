package com.iostresstest.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drops an EICAR test file into each worker directory before the test and verifies
 * it is intact afterwards. A missing or altered canary indicates that AV software
 * on the server intercepted or quarantined the file during the test.
 *
 * The EICAR content is never stored as a plain string in this source file. It is
 * split into four parts and each part is XOR-encoded with a distinct key so that
 * no recognisable fragment appears in the compiled class bytecode, preventing AV
 * from flagging the tool itself.
 */
public class CanaryManager {

    public static final String CANARY_FILENAME = "eicar-canary.com";

    // ── XOR-obfuscated EICAR parts ───────────────────────────────────────────
    // To regenerate: take the 68-byte EICAR test string, split into four runs,
    // XOR each byte with the corresponding key, and store the result here.

    // Part 1 — chars 0..19, key 0x37
    private static final byte P1K = 0x37;
    private static final byte[] P1  = {
        0x6F, 0x02, 0x78, 0x16, 0x67, 0x12, 0x77, 0x76,
        0x67, 0x6C, 0x03, 0x6B, 0x67, 0x6D, 0x6F, 0x02,
        0x03, 0x1F, 0x67, 0x69
    };

    // Part 2 — chars 20..39, key 0x55
    private static final byte P2K = 0x55;
    private static final byte[] P2  = {
        0x7C, 0x62, 0x16, 0x16, 0x7C, 0x62, 0x28, 0x71,
        0x10, 0x1C, 0x16, 0x14, 0x07, 0x78, 0x06, 0x01,
        0x14, 0x1B, 0x11, 0x14
    };

    // Part 3 — chars 40..59, key 0x12
    private static final byte P3K = 0x12;
    private static final byte[] P3  = {
        0x40, 0x56, 0x3F, 0x53, 0x5C, 0x46, 0x5B, 0x44,
        0x5B, 0x40, 0x47, 0x41, 0x3F, 0x46, 0x57, 0x41,
        0x46, 0x3F, 0x54, 0x5B
    };

    // Part 4 — chars 60..67, key 0x71
    private static final byte P4K = 0x71;
    private static final byte[] P4  = {
        0x3D, 0x34, 0x50, 0x55, 0x39, 0x5A, 0x39, 0x5B
    };
    // ────────────────────────────────────────────────────────────────────────

    /** Maps canary file path → the exact bytes that were written. */
    private final Map<Path, byte[]> dropped = new LinkedHashMap<>();

    /**
     * Writes a canary file into each of the supplied directories.
     * Directories are created if they do not exist.
     */
    public void drop(List<Path> directories) throws IOException {
        byte[] content = buildContent();
        for (Path dir : directories) {
            Files.createDirectories(dir);
            Path canary = dir.resolve(CANARY_FILENAME);
            Files.write(canary, content);
            dropped.put(canary, content);
        }
    }

    /**
     * Reads each canary file and compares it to the bytes that were written.
     * Returns one {@link CanaryResult} per directory.
     */
    public List<CanaryResult> verify() {
        List<CanaryResult> results = new ArrayList<>();
        for (Map.Entry<Path, byte[]> entry : dropped.entrySet()) {
            Path file     = entry.getKey();
            byte[] expect = entry.getValue();
            if (!Files.exists(file)) {
                results.add(new CanaryResult(file, CanaryResult.Status.MISSING, null));
            } else {
                try {
                    byte[] actual = Files.readAllBytes(file);
                    if (Arrays.equals(actual, expect)) {
                        results.add(new CanaryResult(file, CanaryResult.Status.OK, null));
                    } else {
                        String observed = new String(actual, StandardCharsets.UTF_8);
                        results.add(new CanaryResult(file, CanaryResult.Status.ALTERED, observed));
                    }
                } catch (IOException e) {
                    results.add(new CanaryResult(file, CanaryResult.Status.MISSING, e.getMessage()));
                }
            }
        }
        return results;
    }

    /** Deletes all canary files that were written by this manager. */
    public void cleanup() {
        for (Path file : dropped.keySet()) {
            try { Files.deleteIfExists(file); } catch (IOException ignored) {}
        }
        dropped.clear();
    }

    public boolean hasCanaries() { return !dropped.isEmpty(); }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static byte[] buildContent() {
        byte[] out = new byte[P1.length + P2.length + P3.length + P4.length];
        int pos = 0;
        pos = xor(P1, P1K, out, pos);
        pos = xor(P2, P2K, out, pos);
        pos = xor(P3, P3K, out, pos);
             xor(P4, P4K, out, pos);
        return out;
    }

    private static int xor(byte[] src, byte key, byte[] dst, int offset) {
        for (int i = 0; i < src.length; i++) {
            dst[offset + i] = (byte) (src[i] ^ key);
        }
        return offset + src.length;
    }
}
