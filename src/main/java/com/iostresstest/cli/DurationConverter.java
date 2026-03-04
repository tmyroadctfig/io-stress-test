package com.iostresstest.cli;

import picocli.CommandLine;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationConverter implements CommandLine.ITypeConverter<Duration> {

    private static final Pattern PATTERN = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    @Override
    public Duration convert(String value) throws Exception {
        String trimmed = value.trim();
        Matcher m = PATTERN.matcher(trimmed);
        if (!m.matches() || trimmed.isEmpty()) {
            throw new CommandLine.TypeConversionException(
                    "Invalid duration '" + value + "'. Use formats like: 2h, 30m, 1h30m, 10s");
        }
        long hours   = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
        long minutes = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
        long seconds = m.group(3) != null ? Long.parseLong(m.group(3)) : 0;
        Duration d = Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
        if (d.isZero()) {
            throw new CommandLine.TypeConversionException("Duration must be positive");
        }
        return d;
    }
}
