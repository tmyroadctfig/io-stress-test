package com.iostresstest.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class CorpusSpecTest {

    private final CorpusSpec.Converter converter = new CorpusSpec.Converter();

    @Test
    void convert_unixPath() throws Exception {
        CorpusSpec spec = converter.convert("/mnt/share/corpus:500");
        assertEquals("/mnt/share/corpus", spec.getDirectory().toString());
        assertEquals(500, spec.getFileCount());
    }

    @Test
    void convert_relativePath() throws Exception {
        CorpusSpec spec = converter.convert("corpus:100");
        assertEquals("corpus", spec.getDirectory().toString());
        assertEquals(100, spec.getFileCount());
    }

    @Test
    void convert_windowsDriveLetter_splitsOnLastColon() throws Exception {
        // C:\work\corpus:200 — last colon separates path from count
        CorpusSpec spec = converter.convert("C:\\work\\corpus:200");
        assertEquals("C:\\work\\corpus", spec.getDirectory().toString());
        assertEquals(200, spec.getFileCount());
    }

    @Test
    void convert_uncPath() throws Exception {
        CorpusSpec spec = converter.convert("\\\\server\\share\\corpus:50");
        assertEquals(50, spec.getFileCount());
        assertNotNull(spec.getDirectory());
    }

    @Test
    void convert_countOfOne() throws Exception {
        CorpusSpec spec = converter.convert("/tmp/corpus:1");
        assertEquals(1, spec.getFileCount());
    }

    @Test
    void convert_zeroCount_throws() {
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("/tmp/corpus:0"));
    }

    @Test
    void convert_negativeCount_throws() {
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("/tmp/corpus:-1"));
    }

    @Test
    void convert_noColon_throws() {
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("/tmp/corpus"));
    }

    @Test
    void convert_nonNumericCount_throws() {
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("/tmp/corpus:abc"));
    }
}
