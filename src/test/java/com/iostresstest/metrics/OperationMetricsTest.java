package com.iostresstest.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;

import static org.junit.jupiter.api.Assertions.*;

class OperationMetricsTest {

    private OperationMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new OperationMetrics();
    }

    // -------------------------------------------------------------------------
    // Error grouping (normalizeKey)
    // -------------------------------------------------------------------------

    @Nested
    class ErrorGrouping {

        @Test
        void sameExceptionType_sameUncPath_groupedTogether() {
            metrics.recordError(new FileSystemException("\\\\server\\share\\a\\b\\file1.bin", null, "The network path was not found"));
            metrics.recordError(new FileSystemException("\\\\server\\share\\c\\d\\file2.bin", null, "The network path was not found"));

            assertEquals(1, metrics.getErrorDetails().size(), "Different paths with same reason should form one group");
            assertEquals(2, metrics.getErrorDetails().values().iterator().next());
        }

        @Test
        void sameExceptionType_differentReason_groupedSeparately() {
            metrics.recordError(new IOException("No such file or directory: /mnt/data/a.bin"));
            metrics.recordError(new IOException("Permission denied: /mnt/data/b.bin"));

            assertEquals(2, metrics.getErrorDetails().size());
        }

        @Test
        void messageStartingWithPath_keyIsJustClassName() {
            // UNC path — message starts with non-letter, so prefix is empty → key is class name only
            metrics.recordError(new AccessDeniedException("\\\\server\\share\\file.bin"));
            metrics.recordError(new AccessDeniedException("\\\\server\\share\\other.bin"));

            assertEquals(1, metrics.getErrorDetails().size());
            assertTrue(metrics.getErrorDetails().containsKey("AccessDeniedException"));
        }

        @Test
        void messageWithLetterPrefix_keyIncludesPrefix() {
            metrics.recordError(new IOException("No such file or directory: /mnt/a.bin"));
            metrics.recordError(new IOException("No such file or directory: /mnt/b.bin"));

            assertEquals(1, metrics.getErrorDetails().size());
            assertTrue(metrics.getErrorDetails().containsKey("IOException: No such file or directory"));
        }

        @Test
        void nullMessage_keyIsClassName() {
            metrics.recordError(new IOException((String) null));

            assertTrue(metrics.getErrorDetails().containsKey("IOException"));
        }

        @Test
        void emptyMessage_keyIsClassName() {
            metrics.recordError(new IOException(""));

            assertTrue(metrics.getErrorDetails().containsKey("IOException"));
        }

        @Test
        void recordError_noArg_incrementsCountOnly() {
            metrics.recordError();

            assertEquals(1, metrics.getErrorCount());
            assertTrue(metrics.getErrorDetails().isEmpty(), "No-arg recordError should not add to errorDetails");
        }

        @Test
        void errorCount_reflectsTotalAcrossAllGroups() {
            metrics.recordError(new IOException("No such file or directory: /a"));
            metrics.recordError(new IOException("Permission denied: /b"));
            metrics.recordError(new AccessDeniedException("/c"));
            metrics.recordError(); // no-arg

            assertEquals(4, metrics.getErrorCount());
        }
    }

    // -------------------------------------------------------------------------
    // Sample message normalization (normalizeSample)
    // -------------------------------------------------------------------------

    @Nested
    class SampleNormalization {

        @Test
        void uncPath_withReason_pathReplacedWithPlaceholder() {
            metrics.recordError(new FileSystemException(
                    "\\\\192.168.0.56\\Upload\\load-test\\a95\\6d7\\a956d702.bin", null,
                    "The network path was not found"));

            String sample = singleSample();
            assertEquals("FileSystemException: <test-directory>: The network path was not found", sample);
        }

        @Test
        void uncPath_withoutReason_pathReplacedWithPlaceholder() {
            // AccessDeniedException and NoSuchFileException store only the file path as the message
            metrics.recordError(new AccessDeniedException("\\\\192.168.0.56\\Upload\\load-test\\eicar-canary.com"));

            String sample = singleSample();
            assertEquals("AccessDeniedException: <test-directory>", sample);
        }

        @Test
        void uncPath_noSuchFile_pathReplacedWithPlaceholder() {
            metrics.recordError(new NoSuchFileException("\\\\192.168.0.56\\Upload\\load-test\\eicar-canary.com"));

            String sample = singleSample();
            assertEquals("NoSuchFileException: <test-directory>", sample);
        }

        @Test
        void unixPath_withReason_pathReplacedWithPlaceholder() {
            metrics.recordError(new FileSystemException("/mnt/data/corpus/a/b/file.bin", null, "Permission denied"));

            String sample = singleSample();
            assertEquals("FileSystemException: <test-directory>: Permission denied", sample);
        }

        @Test
        void unixPath_withoutReason_pathReplacedWithPlaceholder() {
            metrics.recordError(new AccessDeniedException("/mnt/data/corpus/secret.bin"));

            String sample = singleSample();
            assertEquals("AccessDeniedException: <test-directory>", sample);
        }

        @Test
        void windowsDrivePath_withReason_pathReplacedWithPlaceholder() {
            metrics.recordError(new FileSystemException("C:\\load-test\\a\\b\\file.bin", null, "Access is denied"));

            String sample = singleSample();
            assertEquals("FileSystemException: <test-directory>: Access is denied", sample);
        }

        @Test
        void windowsDrivePath_withoutReason_pathReplacedWithPlaceholder() {
            metrics.recordError(new AccessDeniedException("C:\\load-test\\protected.bin"));

            String sample = singleSample();
            assertEquals("AccessDeniedException: <test-directory>", sample);
        }

        @Test
        void plainMessage_noPath_leftAsIs() {
            // IOException thrown directly with a plain message (no file path prefix)
            metrics.recordError(new IOException("The network path was not found"));

            String sample = singleSample();
            assertEquals("IOException: The network path was not found", sample);
        }

        @Test
        void nullMessage_sampleIsClassName() {
            metrics.recordError(new IOException((String) null));

            String sample = singleSample();
            assertEquals("IOException", sample);
        }

        @Test
        void firstOccurrenceWins_subsequentPathsIgnored() {
            // Same group key — only the first sample should be stored
            metrics.recordError(new AccessDeniedException("\\\\server\\share\\first.bin"));
            metrics.recordError(new AccessDeniedException("\\\\server\\share\\second.bin"));

            assertEquals(1, metrics.getErrorSamples().size());
            assertEquals("AccessDeniedException: <test-directory>", singleSample());
        }

        @Test
        void longVirusReasonPreserved() {
            String reason = "Operation did not complete successfully because the file contains a virus or potentially unwanted software";
            metrics.recordError(new FileSystemException(
                    "\\\\192.168.0.56\\Upload\\load-test\\eicar-canary.com", null, reason));

            String sample = singleSample();
            assertEquals("FileSystemException: <test-directory>: " + reason, sample);
        }

        // Helper: assumes exactly one group exists and returns its sample
        private String singleSample() {
            assertEquals(1, metrics.getErrorSamples().size(), "Expected exactly one error group");
            return metrics.getErrorSamples().values().iterator().next();
        }
    }
}
