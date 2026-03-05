package com.iostresstest.cli;

import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkerSpec {

    public static final int DEFAULT_READ_RATIO_PCT = 50;

    public enum Type {
        READ, LISTING, WRITE, READ_LISTING;

        public static Type parse(String s) {
            switch (s.trim().toLowerCase()) {
                case "read":       return READ;
                case "listing":    return LISTING;
                case "write":      return WRITE;
                case "read+list":  return READ_LISTING;
                default:
                    throw new IllegalArgumentException(
                            "Unknown worker type '" + s + "'. Use: read, listing, write, read+list");
            }
        }
    }

    /** Matches an optional ratio suffix on the type token, e.g. "read+list[75]". */
    private static final Pattern RATIO_PATTERN = Pattern.compile("^(.+?)\\[(\\d+)]$");

    private final Type type;
    private final int threads;
    private final Path directory;
    /** Percentage of iterations that perform a read (vs. listing). Only used for READ_LISTING. */
    private final int readRatioPct;

    public WorkerSpec(Type type, int threads, Path directory) {
        this(type, threads, directory, DEFAULT_READ_RATIO_PCT);
    }

    public WorkerSpec(Type type, int threads, Path directory, int readRatioPct) {
        this.type         = type;
        this.threads      = threads;
        this.directory    = directory;
        this.readRatioPct = readRatioPct;
    }

    public Type getType()        { return type; }
    public int getThreads()      { return threads; }
    public Path getDirectory()   { return directory; }
    public int getReadRatioPct() { return readRatioPct; }

    @Override
    public String toString() {
        String typeStr = type == Type.READ_LISTING ? "read+list" : type.name().toLowerCase();
        String ratioSuffix = (type == Type.READ_LISTING && readRatioPct != DEFAULT_READ_RATIO_PCT)
                ? "[" + readRatioPct + "]" : "";
        return typeStr + ratioSuffix + ":" + threads + ":" + directory;
    }

    public static class Converter implements CommandLine.ITypeConverter<WorkerSpec> {
        @Override
        public WorkerSpec convert(String value) throws Exception {
            String[] parts = value.split(":", 3);
            if (parts.length != 3) {
                throw new CommandLine.TypeConversionException(
                        "Invalid worker spec '" + value + "'. Format: type:threads:directory  " +
                        "(e.g. read:10:/mnt/evidence or read+list[75]:8:/mnt/data)");
            }

            // Parse optional ratio from type token, e.g. "read+list[75]"
            String typeToken = parts[0].trim();
            int readRatioPct = DEFAULT_READ_RATIO_PCT;
            boolean hasRatio = false;
            Matcher m = RATIO_PATTERN.matcher(typeToken);
            if (m.matches()) {
                typeToken = m.group(1);
                int ratio = Integer.parseInt(m.group(2));
                if (ratio < 0 || ratio > 100) {
                    throw new CommandLine.TypeConversionException(
                            "Read ratio must be between 0 and 100 in worker spec '" + value + "'");
                }
                readRatioPct = ratio;
                hasRatio = true;
            }

            Type type;
            try {
                type = Type.parse(typeToken);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(e.getMessage());
            }

            if (hasRatio && type != Type.READ_LISTING) {
                throw new CommandLine.TypeConversionException(
                        "Ratio suffix [n] is only valid for read+list workers");
            }

            int threads;
            try {
                threads = Integer.parseInt(parts[1].trim());
                if (threads < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new CommandLine.TypeConversionException(
                        "Thread count must be a positive integer in worker spec '" + value + "'");
            }

            Path directory = Paths.get(parts[2].trim());
            return new WorkerSpec(type, threads, directory, readRatioPct);
        }
    }
}
