package com.iostresstest.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DurationConverterTest {

    private final DurationConverter converter = new DurationConverter();

    @Test
    void convert_hoursOnly() throws Exception {
        assertEquals(Duration.ofHours(2), converter.convert("2h"));
    }

    @Test
    void convert_minutesOnly() throws Exception {
        assertEquals(Duration.ofMinutes(30), converter.convert("30m"));
    }

    @Test
    void convert_secondsOnly() throws Exception {
        assertEquals(Duration.ofSeconds(10), converter.convert("10s"));
    }

    @Test
    void convert_hoursAndMinutes() throws Exception {
        assertEquals(Duration.ofHours(1).plusMinutes(30), converter.convert("1h30m"));
    }

    @Test
    void convert_hoursMinutesAndSeconds() throws Exception {
        assertEquals(Duration.ofHours(2).plusMinutes(15).plusSeconds(30), converter.convert("2h15m30s"));
    }

    @Test
    void convert_largeValues() throws Exception {
        assertEquals(Duration.ofHours(100), converter.convert("100h"));
        assertEquals(Duration.ofMinutes(90), converter.convert("90m"));
    }

    @Test
    void convert_zeroDuration_throws() {
        assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert("0h"));
        assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert("0m"));
        assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert("0s"));
    }

    @Test
    void convert_emptyString_throws() {
        assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert(""));
    }

    @Test
    void convert_invalidFormat_throws() {
        assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert("2 hours"));
        assertThrows(CommandLine.TypeConversionException.class, () -> converter.convert("abc"));
    }
}
