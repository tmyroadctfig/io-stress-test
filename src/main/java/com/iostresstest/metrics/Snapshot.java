package com.iostresstest.metrics;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Immutable point-in-time snapshot of all metrics, used by the UI for display and rate calculation.
 */
public class Snapshot {

    public final long nanoTime;
    public final Map<OperationType, TypeSnapshot> byType;

    public Snapshot(long nanoTime, Map<OperationType, TypeSnapshot> byType) {
        this.nanoTime = nanoTime;
        this.byType = Collections.unmodifiableMap(byType);
    }

    public TypeSnapshot get(OperationType type) {
        return byType.get(type);
    }

    public static class TypeSnapshot {
        public final long opCount;
        public final long byteCount;
        public final long errorCount;
        public final long p50Micros;
        public final long p95Micros;
        public final long p99Micros;
        public final long meanMicros;
        public final long maxMicros;
        /** Counts per normalized exception key (class + message prefix). Sorted for stable display. */
        public final SortedMap<String, Long> errorDetails;
        /** One representative full message per normalized key. */
        public final Map<String, String> errorSamples;

        public TypeSnapshot(long opCount, long byteCount, long errorCount,
                            long p50Micros, long p95Micros, long p99Micros,
                            long meanMicros, long maxMicros,
                            Map<String, Long> errorDetails,
                            Map<String, String> errorSamples) {
            this.opCount      = opCount;
            this.byteCount    = byteCount;
            this.errorCount   = errorCount;
            this.p50Micros    = p50Micros;
            this.p95Micros    = p95Micros;
            this.p99Micros    = p99Micros;
            this.meanMicros   = meanMicros;
            this.maxMicros    = maxMicros;
            this.errorDetails = Collections.unmodifiableSortedMap(new TreeMap<>(errorDetails));
            this.errorSamples = Collections.unmodifiableMap(errorSamples);
        }
    }
}
