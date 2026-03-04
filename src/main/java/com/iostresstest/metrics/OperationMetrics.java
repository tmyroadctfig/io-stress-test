package com.iostresstest.metrics;

import org.HdrHistogram.Histogram;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ConcurrentHashMap<String, LongAdder> errorDetails = new ConcurrentHashMap<>();
    /** One representative full message per normalised key, captured on first occurrence. */
    private final ConcurrentHashMap<String, String> errorSamples = new ConcurrentHashMap<>();

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

    public void recordError(Exception e) {
        errorCount.increment();
        String key = normalizeKey(e);
        errorDetails.computeIfAbsent(key, k -> new LongAdder()).increment();
        errorSamples.putIfAbsent(key, e.getClass().getSimpleName()
                + (e.getMessage() != null ? ": " + e.getMessage() : ""));
    }

    /** Groups by class name + the leading word/phrase up to the first non-letter, non-space char. */
    private static String normalizeKey(Exception e) {
        String cls = e.getClass().getSimpleName();
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) return cls;
        int end = 0;
        while (end < msg.length() && (Character.isLetter(msg.charAt(end)) || msg.charAt(end) == ' ')) {
            end++;
        }
        String prefix = msg.substring(0, end).stripTrailing();
        return prefix.isEmpty() ? cls : cls + ": " + prefix;
    }

    public Map<String, Long> getErrorDetails() {
        Map<String, Long> result = new HashMap<>();
        errorDetails.forEach((k, v) -> result.put(k, v.sum()));
        return Collections.unmodifiableMap(result);
    }

    public Map<String, String> getErrorSamples() {
        return Collections.unmodifiableMap(errorSamples);
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
