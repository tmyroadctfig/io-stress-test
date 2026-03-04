package com.iostresstest.phase;

import com.iostresstest.corpus.CorpusManager;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes all synthetic files registered with the CorpusManager.
 * Time spent here is tracked but does not count towards test metrics.
 */
public class CleanupPhase {

    private final CorpusManager corpusManager;
    private final CleanupProgressListener listener;

    public interface CleanupProgressListener {
        void onProgress(long deleted, long total);
    }

    public CleanupPhase(CorpusManager corpusManager, CleanupProgressListener listener) {
        this.corpusManager = corpusManager;
        this.listener      = listener;
    }

    public PhaseResult run() {
        Instant start = Instant.now();
        try {
            corpusManager.cleanup(counts -> listener.onProgress(counts[0], counts[1]));
            return PhaseResult.success("Cleanup", Duration.between(start, Instant.now()));
        } catch (Exception e) {
            return PhaseResult.failure("Cleanup", Duration.between(start, Instant.now()),
                    "Cleanup failed: " + e.getMessage());
        }
    }
}
