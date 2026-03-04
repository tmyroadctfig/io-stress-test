package com.iostresstest;

import com.iostresstest.cli.StressTestCommand;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        AnsiConsole.systemInstall();
        int exitCode = new CommandLine(new StressTestCommand()).execute(args);
        AnsiConsole.systemUninstall();
        System.exit(exitCode);
    }
}
