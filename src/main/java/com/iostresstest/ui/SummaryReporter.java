package com.iostresstest.ui;

import com.iostresstest.corpus.CanaryResult;
import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;
import com.iostresstest.metrics.Snapshot;
import com.iostresstest.phase.PhaseResult;
import org.fusesource.jansi.Ansi.Color;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Prints the final metrics summary table after the test and cleanup phases complete.
 */
public class SummaryReporter {

    // Column format: 1+20+1+8+1+11+1+12+1+9+1+9+1+9 = 83 visible chars
    private static final String COL_FMT = " %-20s %8s %11s %12s %9s %9s %9s";
    private static final int WIDTH = 83;
    private static final String H = "─".repeat(WIDTH);

    public void print(MetricsRegistry metrics, List<PhaseResult> phases,
                      List<CanaryResult> canaryResults) {
        Snapshot snap = metrics.snapshot(System.nanoTime());

        System.out.println();
        System.out.println(ansi().bold().fg(Color.CYAN).a("┌" + H + "┐").reset());
        printRow(center("IO STRESS TEST — RESULTS", WIDTH), WIDTH);
        System.out.println(ansi().bold().fg(Color.CYAN).a("├" + H + "┤").reset());

        // Phase timings
        printRow(bold(String.format(" %-12s  %-10s  %s", "PHASE", "DURATION", "STATUS")), WIDTH);
        for (PhaseResult phase : phases) {
            String status = phase.isSucceeded()
                    ? ansi().fg(Color.GREEN).a("OK").reset().toString()
                    : ansi().fg(Color.RED).a("FAILED").reset().toString();
            String line = String.format(" %-12s  %-10s  %s",
                    phase.getName(), formatDuration(phase.getElapsed()), status);
            if (!phase.isSucceeded() && phase.getMessage() != null) {
                line += "  " + phase.getMessage();
            }
            printRow(line, WIDTH);
        }

        // Resolve test duration for rate calculation
        Duration testDuration = phases.stream()
                .filter(p -> "Test".equals(p.getName()))
                .findFirst()
                .map(PhaseResult::getElapsed)
                .orElse(null);
        double testSecs = (testDuration != null && testDuration.toMillis() > 0)
                ? testDuration.toMillis() / 1000.0 : 0;

        // Metrics table
        System.out.println(ansi().bold().fg(Color.CYAN).a("├" + H + "┤").reset());
        printRow(bold(String.format(COL_FMT,
                "OPERATION", "OPS", "BYTES", "RATE", "AVG μs", "p95 μs", "p99 μs")), WIDTH);
        System.out.println(ansi().bold().fg(Color.CYAN).a("├" + H + "┤").reset());

        for (OperationType type : OperationType.values()) {
            Snapshot.TypeSnapshot ts = snap.get(type);
            if (ts.opCount == 0 && ts.errorCount == 0) continue;

            String bytesStr = ts.byteCount > 0 ? formatBytes(ts.byteCount) : "N/A";
            String rateStr  = (type.hasThroughput() && testSecs > 0 && ts.byteCount > 0)
                    ? formatBytes((long) (ts.byteCount / testSecs)) + "/s"
                    : "N/A";

            String line = String.format(COL_FMT,
                    type.getDisplayName(),
                    formatLong(ts.opCount),
                    bytesStr,
                    rateStr,
                    formatLong(ts.meanMicros),
                    formatLong(ts.p95Micros),
                    formatLong(ts.p99Micros));
            printRow(line, WIDTH);

            if (ts.errorCount > 0) {
                if (ts.errorDetails.isEmpty()) {
                    String errLine = String.format("  %-19s %s errors",
                            "↳",
                            ansi().fg(Color.RED).a(String.valueOf(ts.errorCount)).reset());
                    printRow(errLine, WIDTH);
                } else {
                    for (Map.Entry<String, Long> entry : ts.errorDetails.entrySet()) {
                        String sample = ts.errorSamples.getOrDefault(entry.getKey(), entry.getKey());
                        String errLine = "   ↳  " + ansi().fg(Color.RED)
                                .a(String.format("%,d", entry.getValue()) + "x  " + sample)
                                .reset();
                        printRow(errLine, WIDTH);
                    }
                }
            }
        }

        // Canary results (only printed when --canary was used)
        if (!canaryResults.isEmpty()) {
            System.out.println(ansi().bold().fg(Color.CYAN).a("├" + H + "┤").reset());
            printRow(bold(" CANARY (EICAR)"), WIDTH);
            System.out.println(ansi().bold().fg(Color.CYAN).a("├" + H + "┤").reset());
            for (CanaryResult cr : canaryResults) {
                String dirStr = " " + cr.getFile().getParent().toString();
                switch (cr.getStatus()) {
                    case OK:
                        printRow(ansi().fg(Color.GREEN).a("  ✓ ").reset() + dirStr + "  "
                                + ansi().fg(Color.GREEN).a("OK").reset(), WIDTH);
                        break;
                    case MISSING:
                        printRow(ansi().fg(Color.RED).a("  ✗ ").reset() + dirStr + "  "
                                + ansi().fg(Color.RED).a("MISSING")
                                .a(cr.getDetail() != null ? " — " + cr.getDetail() : "").reset(),
                                WIDTH);
                        break;
                    case ALTERED:
                        printRow(ansi().fg(Color.RED).a("  ✗ ").reset() + dirStr + "  "
                                + ansi().fg(Color.RED).a("ALTERED").reset(), WIDTH);
                        // Show the first 60 chars of what was found, on a second line
                        String observed = cr.getDetail() != null ? cr.getDetail() : "";
                        if (observed.length() > 60) observed = observed.substring(0, 60) + "…";
                        printRow("      " + ansi().fgBrightBlack()
                                .a("found: \"" + observed + "\"").reset(), WIDTH);
                        break;
                }
            }
        }

        System.out.println(ansi().bold().fg(Color.CYAN).a("└" + H + "┘").reset());
        System.out.println();
    }

    private static void printRow(String content, int width) {
        String visible = content.replaceAll("\u001B\\[[;\\d]*[A-Za-z]", "");
        int pad = Math.max(0, width - visible.length());
        System.out.println(ansi().bold().fg(Color.CYAN).a("│").reset()
                + content + " ".repeat(pad)
                + ansi().bold().fg(Color.CYAN).a("│").reset());
    }

    private static String bold(String s) {
        return ansi().bold().a(s).reset().toString();
    }

    private static String center(String s, int width) {
        int pad = Math.max(0, width - s.length());
        int left = pad / 2;
        int right = pad - left;
        return " ".repeat(left) + ansi().bold().a(s).reset() + " ".repeat(right);
    }

    private static String formatDuration(java.time.Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        long ms = d.toMillisPart();
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return String.format("%d.%03ds", s, ms);
    }

    private static String formatLong(long v) {
        return String.format("%,d", v);
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.2f GiB", bytes / (1024.0 * 1024 * 1024));
        } else if (bytes >= 1024L * 1024) {
            return String.format("%.2f MiB", bytes / (1024.0 * 1024));
        } else if (bytes >= 1024L) {
            return String.format("%.2f KiB", bytes / 1024.0);
        }
        return bytes + " B";
    }
}
