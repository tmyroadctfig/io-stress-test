package com.iostresstest.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class SizeConverterTest {

    private final SizeConverter converter = new SizeConverter();

    @ParameterizedTest
    @CsvSource({
        "1KiB,          1024",
        "512KiB,        524288",
        "1MiB,          1048576",
        "256MiB,        268435456",
        "1GiB,          1073741824",
        "1KB,           1000",
        "1MB,           1000000",
        "1GB,           1000000000",
        "1024,          1024",
        "512,           512",
        "1B,            1"
    })
    void convert_validInputs(String input, long expected) throws Exception {
        assertEquals(expected, converter.convert(input));
    }

    @Test
    void convert_caseInsensitive() throws Exception {
        assertEquals(1024L, converter.convert("1kib"));
        assertEquals(1024L, converter.convert("1KIB"));
        assertEquals(1048576L, converter.convert("1mib"));
        assertEquals(1073741824L, converter.convert("1gib"));
    }

    @Test
    void convert_fractionalValues() throws Exception {
        assertEquals(512L, converter.convert("0.5KiB"));
        assertEquals(1536L, converter.convert("1.5KiB"));
    }

    @Test
    void convert_invalidInput_throws() {
        assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert("invalid"));
        assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert("MiB"));
        assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert(""));
    }
}
