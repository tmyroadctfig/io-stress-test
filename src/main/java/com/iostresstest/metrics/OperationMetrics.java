package com.iostresstest.metrics;

import org.HdrHistogram.Histogram;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe metrics for a single operation type.
 * Latencies are recorded in microseconds.
 */
public class OperationMetrics {

    // Max trackable latency: 10 minutes in microseconds, 3 significant digits
    private static final long MAX_LATENCY_MICROS = TimeUnit.MINUTES.toMicros(10);

    private final LongAdder opCount    = new LongAdder();
    private final LongAdder byteCount  = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    private final Histogram histogram  = new Histogram(MAX_LATENCY_MICROS, 3);

    public void record(long durationNanos, long bytes) {
        opCount.increment();
        byteCount.add(bytes);
        long micros = Math.max(1, durationNanos / 1_000);
        synchronized (histogram) {
            histogram.recordValue(micros);
        }
    }

    public void recordError() {
        errorCount.increment();
    }

    public long getOpCount()    { return opCount.sum(); }
    public long getByteCount()  { return byteCount.sum(); }
    public long getErrorCount() { return errorCount.sum(); }

    public long getP50Micros() {
        synchronized (histogram) { return histogram.getValueAtPercentile(50.0); }
    }

    public long getP95Micros() {
        synchronized (histogram) { return histogram.getValueAtPercentile(95.0); }
    }

    public long getP99Micros() {
        synchronized (histogram) { return histogram.getValueAtPercentile(99.0); }
    }

    public long getMeanMicros() {
        synchronized (histogram) { return (long) histogram.getMean(); }
    }

    public long getMaxMicros() {
        synchronized (histogram) { return histogram.getMaxValue(); }
    }
}
