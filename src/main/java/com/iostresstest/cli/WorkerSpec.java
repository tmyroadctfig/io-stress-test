package com.iostresstest.cli;

import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkerSpec {

    public static final int DEFAULT_READ_RATIO_PCT = 50;

    public enum Type {
        READ, LISTING, WRITE, READ_LISTING, META_LISTING;

        public static Type parse(String s) {
            switch (s.trim().toLowerCase()) {
                case "read":          return READ;
                case "listing":       return LISTING;
                case "write":         return WRITE;
                case "read+list":     return READ_LISTING;
                case "metadata+list": return META_LISTING;
                default:
                    throw new IllegalArgumentException(
                            "Unknown worker type '" + s + "'. Use: read, listing, write, read+list, metadata+list");
            }
        }
    }

    /**
     * Matches an optional bracket suffix on the type token.
     * Captures: group 1 = type string, group 2 = ratio digits, group 3 = optional flag word.
     * Examples: "read+list[75]", "metadata+list[50,noopen]"
     */
    private static final Pattern RATIO_PATTERN = Pattern.compile("^(.+?)\\[(\\d+)(?:,(\\w+))?]$");

    private final Type type;
    private final int threads;
    private final Path directory;
    /** Percentage of iterations that perform the primary operation (read/metadata vs. listing). */
    private final int readRatioPct;
    /** Whether metadata+list workers also open and close each file (default true). */
    private final boolean fileOpen;

    public WorkerSpec(Type type, int threads, Path directory) {
        this(type, threads, directory, DEFAULT_READ_RATIO_PCT, true);
    }

    public WorkerSpec(Type type, int threads, Path directory, int readRatioPct) {
        this(type, threads, directory, readRatioPct, true);
    }

    public WorkerSpec(Type type, int threads, Path directory, int readRatioPct, boolean fileOpen) {
        this.type         = type;
        this.threads      = threads;
        this.directory    = directory;
        this.readRatioPct = readRatioPct;
        this.fileOpen     = fileOpen;
    }

    public Type getType()        { return type; }
    public int getThreads()      { return threads; }
    public Path getDirectory()   { return directory; }
    public int getReadRatioPct() { return readRatioPct; }
    public boolean isFileOpen()  { return fileOpen; }

    @Override
    public String toString() {
        String typeStr;
        if (type == Type.READ_LISTING)   typeStr = "read+list";
        else if (type == Type.META_LISTING) typeStr = "metadata+list";
        else typeStr = type.name().toLowerCase();

        String ratioSuffix = "";
        if (type == Type.READ_LISTING && readRatioPct != DEFAULT_READ_RATIO_PCT) {
            ratioSuffix = "[" + readRatioPct + "]";
        } else if (type == Type.META_LISTING) {
            boolean needsBracket = readRatioPct != DEFAULT_READ_RATIO_PCT || !fileOpen;
            if (needsBracket) {
                ratioSuffix = "[" + readRatioPct + (!fileOpen ? ",noopen" : "") + "]";
            }
        }
        return typeStr + ratioSuffix + ":" + threads + ":" + directory;
    }

    public static class Converter implements CommandLine.ITypeConverter<WorkerSpec> {
        @Override
        public WorkerSpec convert(String value) throws Exception {
            String[] parts = value.split(":", 3);
            if (parts.length != 3) {
                throw new CommandLine.TypeConversionException(
                        "Invalid worker spec '" + value + "'. Format: type:threads:directory  " +
                        "(e.g. read:10:/mnt/evidence, read+list[75]:8:/mnt/data, or metadata+list[50,noopen]:4:/mnt/data)");
            }

            // Parse optional bracket suffix from type token, e.g. "read+list[75]" or "metadata+list[50,noopen]"
            String typeToken = parts[0].trim();
            int readRatioPct = DEFAULT_READ_RATIO_PCT;
            boolean fileOpen = true;
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

                String flag = m.group(3);  // optional keyword after comma, e.g. "noopen"
                if (flag != null) {
                    if (!"noopen".equals(flag)) {
                        throw new CommandLine.TypeConversionException(
                                "Unknown option '" + flag + "' in worker spec '" + value + "'. Valid option: noopen");
                    }
                    fileOpen = false;
                }
            }

            Type type;
            try {
                type = Type.parse(typeToken);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(e.getMessage());
            }

            if (hasRatio && type != Type.READ_LISTING && type != Type.META_LISTING) {
                throw new CommandLine.TypeConversionException(
                        "Ratio suffix [n] is only valid for read+list and metadata+list workers");
            }
            if (!fileOpen && type != Type.META_LISTING) {
                throw new CommandLine.TypeConversionException(
                        "Option 'noopen' is only valid for metadata+list workers");
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
            return new WorkerSpec(type, threads, directory, readRatioPct, fileOpen);
        }
    }
}
