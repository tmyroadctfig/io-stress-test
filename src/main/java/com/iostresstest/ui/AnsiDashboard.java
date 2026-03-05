package com.iostresstest.ui;

import com.iostresstest.cli.WorkerSpec;
import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.metrics.OperationType;
import com.iostresstest.metrics.Snapshot;
import com.iostresstest.worker.WorkerGroup;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Live ANSI terminal dashboard. Redraws in-place on each refresh call.
 */
public class AnsiDashboard {

    private static final int BAR_WIDTH      = 20;
    private static final int PANEL_WIDTH    = 76;
    private static final String H_LINE      = "─".repeat(PANEL_WIDTH);
    private static final String TOP         = "┌" + H_LINE + "┐";
    private static final String BOTTOM      = "└" + H_LINE + "┘";
    private static final String MID         = "├" + H_LINE + "┤";

    private boolean firstRender = true;
    private int lastLineCount   = 0;

    // Previous snapshot for rate calculation
    private Snapshot prevSnapshot = null;

    public synchronized void refresh(MetricsRegistry metrics, Instant testStart,
                                     Duration testDuration, List<WorkerGroup> groups,
                                     boolean running) {
        long nowNanos = System.nanoTime();
        Snapshot snap = metrics.snapshot(nowNanos);

        StringBuilder sb = new StringBuilder();

        if (!firstRender) {
            // Move cursor back up to overwrite previous output
            sb.append(ansi().cursorUpLine(lastLineCount).toString());
        }

        Duration elapsed   = Duration.between(testStart, Instant.now());
        Duration remaining = testDuration.minus(elapsed);
        if (remaining.isNegative()) remaining = Duration.ZERO;
        double pct = Math.min(1.0, (double) elapsed.toMillis() / testDuration.toMillis());

        // ── Header ──────────────────────────────────────────────────────────
        String statusLabel = running ? "[ RUNNING ]" : "[COMPLETE ]";
        Color  statusColor = running ? Color.GREEN : Color.CYAN;
        // Compute visible lengths to get padding right
        String leftHead  = "  IO STRESS TEST";
        String rightHead = statusLabel;
        int headPad = PANEL_WIDTH - leftHead.length() - rightHead.length();
        sb.append(row(TOP));
        sb.append(row(cell(
                bold(leftHead) +
                " ".repeat(Math.max(0, headPad)) +
                ansi().fg(statusColor).bold().a(rightHead).reset())));

        // ── Progress bar ─────────────────────────────────────────────────────
        // "  Elapsed: HH:MM:SS  Rem: HH:MM:SS  [bar(20)]  XX.X%"
        //  2+9+8   + 2+5+8    + 2+2+20+1    + 2+5 = ~66 visible chars
        sb.append(row(MID));
        sb.append(row(cell(
                "  Elapsed: " + formatDuration(elapsed) +
                "  Rem: " + formatDuration(remaining) +
                "  " + progressBar(pct, BAR_WIDTH) +
                "  " + String.format("%5.1f%%", pct * 100))));

        // ── Operations table ─────────────────────────────────────────────────
        sb.append(row(MID));
        sb.append(row(cell(
                bold(String.format("  %-20s %8s %12s %12s %7s",
                        "OPERATION", "OPS/S", "THROUGHPUT", "TOTAL OPS", "ERRORS")))));

        for (OperationType type : OperationType.values()) {
            Snapshot.TypeSnapshot cur  = snap.get(type);
            double opsPerSec = calcOpsRate(type, cur, snap.nanoTime);
            double mbPerSec  = calcBytesRate(type, cur, snap.nanoTime);

            String throughput = type.hasThroughput()
                    ? String.format("%8.1f MB/s", mbPerSec)
                    : String.format("%12s", "N/A");

            String errors = cur.errorCount > 0
                    ? ansi().fg(Color.RED).a(String.format("%7d", cur.errorCount)).reset().toString()
                    : String.format("%7d", cur.errorCount);

            sb.append(row(cell(String.format("  %-20s %8.1f %s %12d %s",
                    type.getDisplayName(), opsPerSec, throughput, cur.opCount, errors))));
        }

        // ── Workers table ────────────────────────────────────────────────────
        sb.append(row(MID));
        sb.append(row(cell(bold(String.format("  %-10s %-6s %-35s %s",
                "TYPE", "THDS", "DIRECTORY", "OPS")))));

        for (WorkerGroup g : groups) {
            var type = g.getSpec().getType();
            String typeName = type == WorkerSpec.Type.READ_LISTING ? "read+list" : type.name().toLowerCase();
            String dirName  = abbreviate(g.getSpec().getDirectory().toString(), 35);
            long totalOps   = totalOpsForGroup(g, snap);
            sb.append(row(cell(String.format("  %-10s %-6d %-35s %,d",
                    typeName, g.getSpec().getThreads(), dirName, totalOps))));
        }

        sb.append(row(BOTTOM));

        if (running) {
            sb.append(ansi().fgBrightBlack().a("  Press Ctrl+C to stop early").reset()).append("\n");
        } else {
            sb.append("\n");
        }

        int lineCount = countLines(sb.toString());
        System.out.print(sb);
        System.out.flush();

        prevSnapshot  = snap;
        lastLineCount = lineCount;
        firstRender   = false;
    }

    /** Prints a blank line after the dashboard to separate from summary output. */
    public void complete() {
        System.out.println();
    }

    // ── Rate helpers ──────────────────────────────────────────────────────────

    private double calcOpsRate(OperationType type, Snapshot.TypeSnapshot cur, long curNanos) {
        if (prevSnapshot == null) return 0.0;
        Snapshot.TypeSnapshot prev = prevSnapshot.get(type);
        double dtSec = (curNanos - prevSnapshot.nanoTime) / 1e9;
        if (dtSec <= 0) return 0.0;
        return (cur.opCount - prev.opCount) / dtSec;
    }

    private double calcBytesRate(OperationType type, Snapshot.TypeSnapshot cur, long curNanos) {
        if (prevSnapshot == null) return 0.0;
        Snapshot.TypeSnapshot prev = prevSnapshot.get(type);
        double dtSec = (curNanos - prevSnapshot.nanoTime) / 1e9;
        if (dtSec <= 0) return 0.0;
        return ((cur.byteCount - prev.byteCount) / 1e6) / dtSec;
    }

    private long totalOpsForGroup(WorkerGroup g, Snapshot snap) {
        long total = 0;
        switch (g.getSpec().getType()) {
            case READ:
                total = snap.get(OperationType.SEQ_READ).opCount
                      + snap.get(OperationType.RAND_READ).opCount;
                break;
            case LISTING:
                total = snap.get(OperationType.DIR_LIST).opCount;
                break;
            case READ_LISTING:
                total = snap.get(OperationType.SEQ_READ).opCount
                      + snap.get(OperationType.RAND_READ).opCount
                      + snap.get(OperationType.DIR_LIST).opCount;
                break;
            case WRITE:
                total = snap.get(OperationType.FILE_WRITE).opCount;
                break;
        }
        return total;
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    private static String row(String content) {
        return content + "\n";
    }

    private static String cell(String content) {
        // Pad to fill the panel width using visible (ANSI-stripped) length
        String visible = stripAnsi(content);
        int pad = Math.max(0, PANEL_WIDTH - visible.length());
        return "│" + content + " ".repeat(pad) + "│";
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\u001B\\[[;\\d]*[A-Za-z]", "");
    }

    private static String progressBar(double pct, int width) {
        int filled = (int) (pct * width);
        String bar = "█".repeat(filled) + "░".repeat(width - filled);
        return ansi().fg(Color.GREEN).a("[").a(bar).a("]").reset().toString();
    }

    private static String bold(String s) {
        return ansi().bold().a(s).reset().toString();
    }

    private static String formatDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private static String abbreviate(String s, int max) {
        if (s.length() <= max) return s;
        return "..." + s.substring(s.length() - (max - 3));
    }

    private static int countLines(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') count++;
        }
        return count;
    }
}
