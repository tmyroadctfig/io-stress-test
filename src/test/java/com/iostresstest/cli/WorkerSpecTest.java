package com.iostresstest.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class WorkerSpecTest {

    private final WorkerSpec.Converter converter = new WorkerSpec.Converter();

    @Test
    void convert_readWorker() throws Exception {
        WorkerSpec spec = converter.convert("read:10:/mnt/evidence");
        assertEquals(WorkerSpec.Type.READ, spec.getType());
        assertEquals(10, spec.getThreads());
        assertEquals("/mnt/evidence", spec.getDirectory().toString());
    }

    @Test
    void convert_listingWorker() throws Exception {
        WorkerSpec spec = converter.convert("listing:4:/mnt/data");
        assertEquals(WorkerSpec.Type.LISTING, spec.getType());
        assertEquals(4, spec.getThreads());
        assertEquals("/mnt/data", spec.getDirectory().toString());
    }

    @Test
    void convert_writeWorker() throws Exception {
        WorkerSpec spec = converter.convert("write:8:/mnt/output");
        assertEquals(WorkerSpec.Type.WRITE, spec.getType());
        assertEquals(8, spec.getThreads());
        assertEquals("/mnt/output", spec.getDirectory().toString());
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
        assertEquals("write:4:/tmp/output", spec.toString());
    }
}
