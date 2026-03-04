package com.iostresstest.cli;

import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;

public class WorkerSpec {

    public enum Type {
        READ, LISTING, WRITE;

        public static Type parse(String s) {
            switch (s.trim().toLowerCase()) {
                case "read":    return READ;
                case "listing": return LISTING;
                case "write":   return WRITE;
                default:
                    throw new IllegalArgumentException(
                            "Unknown worker type '" + s + "'. Use: read, listing, write");
            }
        }
    }

    private final Type type;
    private final int threads;
    private final Path directory;

    public WorkerSpec(Type type, int threads, Path directory) {
        this.type = type;
        this.threads = threads;
        this.directory = directory;
    }

    public Type getType()        { return type; }
    public int getThreads()      { return threads; }
    public Path getDirectory()   { return directory; }

    @Override
    public String toString() {
        return type.name().toLowerCase() + ":" + threads + ":" + directory;
    }

    public static class Converter implements CommandLine.ITypeConverter<WorkerSpec> {
        @Override
        public WorkerSpec convert(String value) throws Exception {
            String[] parts = value.split(":", 3);
            if (parts.length != 3) {
                throw new CommandLine.TypeConversionException(
                        "Invalid worker spec '" + value + "'. Format: type:threads:directory  " +
                        "(e.g. read:10:/mnt/evidence)");
            }
            Type type;
            try {
                type = Type.parse(parts[0]);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(e.getMessage());
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
            return new WorkerSpec(type, threads, directory);
        }
    }
}
