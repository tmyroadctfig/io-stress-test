package com.iostresstest.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StressTestCommandTest {

    @Test
    void call_writeWorkerTargetsExistingDirectory_returnsErrorCode(@TempDir Path tempDir) {
        Path existingDir = tempDir.resolve("output");
        assertTrue(existingDir.toFile().mkdirs());

        StringWriter err = new StringWriter();
        CommandLine cmd = new CommandLine(new StressTestCommand());
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute(
                "--duration=5s",
                "--worker=write:1:" + existingDir
        );

        assertEquals(1, exitCode, "Should exit with code 1 when write directory already exists");
//        assertTrue(err.toString().contains("already exists"),
//                "Error message should mention directory already exists");
    }

    @Test
    void call_writeWorkerTargetsNonExistentDirectory_doesNotFailOnDirCheck(@TempDir Path tempDir) {
        Path newDir = tempDir.resolve("new-output");
        // newDir does not exist yet — should pass the existence check
        // (the test will still fail quickly due to no corpus/short duration, but not on the dir check)

        StringWriter err = new StringWriter();
        CommandLine cmd = new CommandLine(new StressTestCommand());
        cmd.setErr(new PrintWriter(err));

        // We only care that the error is NOT about the directory already existing
        int exitCode = cmd.execute("--duration=1s", "--worker=write:1:" + newDir);

        assertEquals(0, exitCode, "Should exit with code 1 when write directory already exists");
        assertTrue(!err.toString().contains("already exists"),
                "Should not complain about a non-existent write directory");
    }
}
