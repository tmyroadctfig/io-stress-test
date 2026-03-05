package com.iostresstest.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class WorkerSpecTest {

    private final WorkerSpec.Converter converter = new WorkerSpec.Converter();

    @Test
    void convert_readWorker() throws Exception {
        WorkerSpec spec = converter.convert("read:10:/mnt/evidence");
        assertEquals(WorkerSpec.Type.READ, spec.getType());
        assertEquals(10, spec.getThreads());
        assertEquals(Paths.get("/mnt/evidence"), spec.getDirectory());
    }

    @Test
    void convert_listingWorker() throws Exception {
        WorkerSpec spec = converter.convert("listing:4:/mnt/data");
        assertEquals(WorkerSpec.Type.LISTING, spec.getType());
        assertEquals(4, spec.getThreads());
        assertEquals(Paths.get("/mnt/data"), spec.getDirectory());
    }

    @Test
    void convert_writeWorker() throws Exception {
        WorkerSpec spec = converter.convert("write:8:/mnt/output");
        assertEquals(WorkerSpec.Type.WRITE, spec.getType());
        assertEquals(8, spec.getThreads());
        assertEquals(Paths.get("/mnt/output"), spec.getDirectory());
    }

    @Test
    void convert_directoryAsThirdSegment_capturesRemainder() throws Exception {
        // split(":", 3) ensures directory path can itself contain colons (Windows drive letter)
        WorkerSpec spec = converter.convert("read:10:C:\\evidence");
        assertEquals(WorkerSpec.Type.READ, spec.getType());
        assertEquals(10, spec.getThreads());
        assertNotNull(spec.getDirectory());
    }

    @Test
    void convert_invalidType_throws() {
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("scan:5:/mnt/data"));
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("copy:5:/mnt/data"));
    }

    @Test
    void convert_typeIsCaseInsensitive() throws Exception {
        // Type.parse() lowercases the input, so READ/Read/read are all valid
        assertEquals(WorkerSpec.Type.READ, converter.convert("READ:5:/mnt/data").getType());
        assertEquals(WorkerSpec.Type.READ, converter.convert("Read:5:/mnt/data").getType());
    }

    @Test
    void convert_invalidThreadCount_throws() {
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("read:abc:/mnt/data"));
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("read:0:/mnt/data"));
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("read:-1:/mnt/data"));
    }

    @Test
    void convert_missingParts_throws() {
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("read:10"));
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("read"));
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert(""));
    }

    @Test
    void toString_producesExpectedFormat() throws Exception {
        WorkerSpec spec = converter.convert("write:4:/tmp/output");
        assertEquals("write:4:" + Paths.get("/tmp/output"), spec.toString());
    }

    // read+list worker

    @Test
    void convert_readListingWorker_defaultRatio() throws Exception {
        WorkerSpec spec = converter.convert("read+list:8:/mnt/data");
        assertEquals(WorkerSpec.Type.READ_LISTING, spec.getType());
        assertEquals(8, spec.getThreads());
        assertEquals(Paths.get("/mnt/data"), spec.getDirectory());
        assertEquals(WorkerSpec.DEFAULT_READ_RATIO_PCT, spec.getReadRatioPct());
    }

    @Test
    void convert_readListingWorker_customRatio() throws Exception {
        WorkerSpec spec = converter.convert("read+list[75]:8:/mnt/data");
        assertEquals(WorkerSpec.Type.READ_LISTING, spec.getType());
        assertEquals(75, spec.getReadRatioPct());
    }

    @Test
    void convert_readListingWorker_ratioZero_valid() throws Exception {
        // ratio=0 means 100% listings — valid at the parser level
        WorkerSpec spec = converter.convert("read+list[0]:4:/mnt/data");
        assertEquals(0, spec.getReadRatioPct());
    }

    @Test
    void convert_readListingWorker_ratio100_valid() throws Exception {
        WorkerSpec spec = converter.convert("read+list[100]:4:/mnt/data");
        assertEquals(100, spec.getReadRatioPct());
    }

    @Test
    void convert_readListingWorker_ratioOutOfRange_throws() {
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("read+list[101]:4:/mnt/data"));
    }

    @Test
    void convert_ratioSuffix_onNonReadListingType_throws() {
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("read[75]:4:/mnt/data"));
        assertThrows(CommandLine.TypeConversionException.class,
                () -> converter.convert("write[50]:4:/mnt/data"));
    }

    @Test
    void toString_readListingWorker_defaultRatio_omitsSuffix() throws Exception {
        WorkerSpec spec = converter.convert("read+list:4:/tmp/data");
        assertEquals("read+list:4:" + Paths.get("/tmp/data"), spec.toString());
    }

    @Test
    void toString_readListingWorker_customRatio_includesSuffix() throws Exception {
        WorkerSpec spec = converter.convert("read+list[75]:4:/tmp/data");
        assertEquals("read+list[75]:4:" + Paths.get("/tmp/data"), spec.toString());
    }
}
