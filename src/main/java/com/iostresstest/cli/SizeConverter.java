package com.iostresstest.cli;

import picocli.CommandLine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SizeConverter implements CommandLine.ITypeConverter<Long> {

    private static final Pattern PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(KiB|MiB|GiB|TiB|KB|MB|GB|TB|B)?",
                    Pattern.CASE_INSENSITIVE);

    @Override
    public Long convert(String value) throws Exception {
        Matcher m = PATTERN.matcher(value.trim());
        if (!m.matches()) {
            throw new CommandLine.TypeConversionException("Invalid size: '" + value + "'");
        }
        double amount = Double.parseDouble(m.group(1));
        String unit = m.group(2) != null ? m.group(2).toUpperCase() : "B";
        long multiplier;
        switch (unit) {
            case "KIB": multiplier = 1024L; break;
            case "MIB": multiplier = 1024L * 1024; break;
            case "GIB": multiplier = 1024L * 1024 * 1024; break;
            case "TIB": multiplier = 1024L * 1024 * 1024 * 1024; break;
            case "KB":  multiplier = 1000L; break;
            case "MB":  multiplier = 1000L * 1000; break;
            case "GB":  multiplier = 1000L * 1000 * 1000; break;
            case "TB":  multiplier = 1000L * 1000 * 1000 * 1000; break;
            default:    multiplier = 1L; break;
        }
        return (long) (amount * multiplier);
    }
}
