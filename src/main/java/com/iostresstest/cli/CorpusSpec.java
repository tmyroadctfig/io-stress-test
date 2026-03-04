package com.iostresstest.cli;

import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Specifies a synthetic file corpus to be created before the test and deleted after.
 * Format: directory:fileCount  (e.g. /tmp/corpus:500)
 */
public class CorpusSpec {

    private final Path directory;
    private final int fileCount;

    public CorpusSpec(Path directory, int fileCount) {
        this.directory = directory;
        this.fileCount = fileCount;
    }

    public Path getDirectory() { return directory; }
    public int getFileCount()  { return fileCount; }

    public static class Converter implements CommandLine.ITypeConverter<CorpusSpec> {
        @Override
        public CorpusSpec convert(String value) throws Exception {
            String[] parts = value.split(":", 2);
            if (parts.length != 2) {
                throw new CommandLine.TypeConversionException(
                        "Invalid corpus spec '" + value + "'. Format: directory:fileCount  " +
                        "(e.g. /tmp/corpus:500)");
            }
            Path directory = Paths.get(parts[0].trim());
            int fileCount;
            try {
                fileCount = Integer.parseInt(parts[1].trim());
                if (fileCount < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new CommandLine.TypeConversionException(
                        "File count must be a positive integer in corpus spec '" + value + "'");
            }
            return new CorpusSpec(directory, fileCount);
        }
    }
}
