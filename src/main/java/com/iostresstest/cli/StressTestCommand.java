package com.iostresstest.cli;

import com.iostresstest.corpus.CorpusManager;
import com.iostresstest.metrics.MetricsRegistry;
import com.iostresstest.phase.CleanupPhase;
import com.iostresstest.phase.PhaseResult;
import com.iostresstest.phase.SetupPhase;
import com.iostresstest.phase.TestPhase;
import com.iostresstest.ui.AnsiDashboard;
import com.iostresstest.ui.SummaryReporter;
import org.fusesource.jansi.Ansi.Color;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.fusesource.jansi.Ansi.ansi;

@Command(
        name = "iostress",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = {
                "IO stress test tool for disk and network file systems.",
                "",
                "Simulates e-discovery workloads: concurrent reads, directory listings,",
                "and writes to reproduce bottlenecks on Windows Server file shares.",
                "",
                "Example:",
                "  iostress --duration=30m --file-size-max=256MiB \\",
                "           --corpus=/tmp/corpus:200 \\",
                "           --worker=read:10:/tmp/corpus \\",
                "           --worker=listing:5:/tmp/corpus \\",
                "           --worker=write:10:/tmp/output"
        }
)
public class StressTestCommand implements Callable<Integer> {

    @Option(names = "--duration", required = true,
            converter = DurationConverter.class,
            description = "Total test duration (e.g. 2h, 30m, 1h30m, 10s)")
    private Duration duration;

    @Option(names = "--file-size-min", defaultValue = "1KiB",
            converter = SizeConverter.class,
            description = "Minimum synthetic file size (default: 1KiB)")
    private long fileSizeMin;

    @Option(names = "--file-size-max", defaultValue = "1GiB",
            converter = SizeConverter.class,
            description = "Maximum synthetic file size (default: 1GiB)")
    private long fileSizeMax;

    @Option(names = "--corpus",
            converter = CorpusSpec.Converter.class,
            description = "Create a synthetic corpus before the test: directory:fileCount " +
                          "(e.g. /tmp/corpus:500). Files are deleted after the test.")
    private CorpusSpec corpus;

    @Option(names = "--worker", required = true, arity = "1..*",
            converter = WorkerSpec.Converter.class,
            description = "Worker specification: type:threads:directory  " +
                          "(types: read, listing, write). Repeatable.")
    private List<WorkerSpec> workers;

    @Override
    public Integer call() {
        if (fileSizeMin > fileSizeMax) {
            System.err.println(ansi().fg(Color.RED)
                    .a("Error: --file-size-min cannot exceed --file-size-max").reset());
            return 1;
        }

        CorpusManager corpusManager = new CorpusManager();
        MetricsRegistry metrics     = new MetricsRegistry();
        AnsiDashboard dashboard     = new AnsiDashboard();
        List<PhaseResult> phases    = new ArrayList<>();

        // ── Setup ────────────────────────────────────────────────────────────
        printPhaseHeader("SETUP");
        SetupPhase setup = new SetupPhase(corpus, fileSizeMin, fileSizeMax, corpusManager,
                (created, total) -> printProgress("Creating corpus", created, total));
        PhaseResult setupResult = setup.run();
        phases.add(setupResult);
        printPhaseResult(setupResult);

        // ── Test ─────────────────────────────────────────────────────────────
        printPhaseHeader("TEST");
        TestPhase test = new TestPhase(duration, workers, fileSizeMin, fileSizeMax,
                metrics, corpusManager, dashboard);
        PhaseResult testResult = test.run();
        phases.add(testResult);

        // ── Cleanup ──────────────────────────────────────────────────────────
        printPhaseHeader("CLEANUP");
        CleanupPhase cleanup = new CleanupPhase(corpusManager,
                (deleted, total) -> printProgress("Deleting files", (int) deleted, (int) total));
        PhaseResult cleanupResult = cleanup.run();
        phases.add(cleanupResult);
        printPhaseResult(cleanupResult);

        // ── Summary ──────────────────────────────────────────────────────────
        new SummaryReporter().print(metrics, phases);

        return 0;
    }

    private static void printPhaseHeader(String name) {
        System.out.println(ansi().bold().fg(Color.CYAN)
                .a("── " + name + " " + "─".repeat(60 - name.length())).reset());
    }

    private static void printPhaseResult(PhaseResult result) {
        if (result.isSucceeded()) {
            System.out.println(ansi().fg(Color.GREEN)
                    .a("  ✓ " + result.getName() + " completed in " +
                       formatDuration(result.getElapsed())).reset());
        } else {
            System.out.println(ansi().fg(Color.RED)
                    .a("  ✗ " + result.getName() + " failed: " + result.getMessage()).reset());
        }
        System.out.println();
    }

    private static void printProgress(String label, int current, int total) {
        int pct = total == 0 ? 100 : (int) ((current * 100L) / total);
        // Overwrite the same line using \r
        System.out.printf("\r  %s: %d / %d  (%d%%)   ", label, current, total, pct);
        System.out.flush();
        if (current == total) System.out.println();
    }

    private static String formatDuration(Duration d) {
        long h  = d.toHours();
        long m  = d.toMinutesPart();
        long s  = d.toSecondsPart();
        long ms = d.toMillisPart();
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return String.format("%d.%03ds", s, ms);
    }
}
