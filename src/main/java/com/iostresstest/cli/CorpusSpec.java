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
            // Split on the last ':' so Windows drive letters (e.g. C:\path) are preserved
            int lastColon = value.lastIndexOf(':');
            if (lastColon < 0) {
                throw new CommandLine.TypeConversionException(
                        "Invalid corpus spec '" + value + "'. Format: directory:fileCount  " +
                        "(e.g. /tmp/corpus:500 or C:\\corpus:500)");
            }
            Path directory = Paths.get(value.substring(0, lastColon).trim());
            int fileCount;
            try {
                fileCount = Integer.parseInt(value.substring(lastColon + 1).trim());
                if (fileCount < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new CommandLine.TypeConversionException(
                        "File count must be a positive integer in corpus spec '" + value + "'");
            }
            return new CorpusSpec(directory, fileCount);
        }
    }
}
