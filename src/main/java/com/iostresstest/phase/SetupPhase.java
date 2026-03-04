package com.iostresstest.phase;

import com.iostresstest.cli.CorpusSpec;
import com.iostresstest.corpus.CorpusManager;

import java.time.Duration;
import java.time.Instant;

/**
 * Creates the synthetic file corpus before the timed test begins.
 * Time spent here is tracked but does not count towards test metrics.
 */
public class SetupPhase {

    private final CorpusSpec corpusSpec;     // may be null if no synthetic corpus requested
    private final long fileSizeMin;
    private final long fileSizeMax;
    private final CorpusManager corpusManager;
    private final SetupProgressListener listener;

    public interface SetupProgressListener {
        void onProgress(int created, int total);
    }

    public SetupPhase(CorpusSpec corpusSpec, long fileSizeMin, long fileSizeMax,
                      CorpusManager corpusManager, SetupProgressListener listener) {
        this.corpusSpec    = corpusSpec;
        this.fileSizeMin   = fileSizeMin;
        this.fileSizeMax   = fileSizeMax;
        this.corpusManager = corpusManager;
        this.listener      = listener;
    }

    public PhaseResult run() {
        Instant start = Instant.now();

        if (corpusSpec == null) {
            return PhaseResult.success("Setup", Duration.between(start, Instant.now()));
        }

        try {
            corpusManager.createCorpus(
                    corpusSpec.getDirectory(),
                    corpusSpec.getFileCount(),
                    fileSizeMin,
                    fileSizeMax,
                    created -> listener.onProgress(created, corpusSpec.getFileCount())
            );
            return PhaseResult.success("Setup", Duration.between(start, Instant.now()));
        } catch (Exception e) {
            return PhaseResult.failure("Setup", Duration.between(start, Instant.now()),
                    "Corpus creation failed: " + e.getMessage());
        }
    }
}
