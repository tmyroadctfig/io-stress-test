package com.iostresstest.metrics;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central registry for all operation metrics, safe for concurrent use.
 */
public class MetricsRegistry {

    private final Map<OperationType, OperationMetrics> metrics = new EnumMap<>(OperationType.class);

    public MetricsRegistry() {
        for (OperationType type : OperationType.values()) {
            metrics.put(type, new OperationMetrics());
        }
    }

    public void record(OperationType type, long durationNanos, long bytes) {
        metrics.get(type).record(durationNanos, bytes);
    }

    public void recordError(OperationType type) {
        metrics.get(type).recordError();
    }

    public OperationMetrics get(OperationType type) {
        return metrics.get(type);
    }

    public Snapshot snapshot(long nanoTime) {
        Map<OperationType, Snapshot.TypeSnapshot> data = new EnumMap<>(OperationType.class);
        for (OperationType type : OperationType.values()) {
            OperationMetrics m = metrics.get(type);
            data.put(type, new Snapshot.TypeSnapshot(
                    m.getOpCount(),
                    m.getByteCount(),
                    m.getErrorCount(),
                    m.getP50Micros(),
                    m.getP95Micros(),
                    m.getP99Micros(),
                    m.getMeanMicros(),
                    m.getMaxMicros()
            ));
        }
        return new Snapshot(nanoTime, data);
    }
}
