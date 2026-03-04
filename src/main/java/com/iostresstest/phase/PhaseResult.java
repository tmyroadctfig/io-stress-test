package com.iostresstest.phase;

import java.time.Duration;

public class PhaseResult {

    private final String name;
    private final Duration elapsed;
    private final boolean succeeded;
    private final String message;

    public PhaseResult(String name, Duration elapsed, boolean succeeded, String message) {
        this.name      = name;
        this.elapsed   = elapsed;
        this.succeeded = succeeded;
        this.message   = message;
    }

    public static PhaseResult success(String name, Duration elapsed) {
        return new PhaseResult(name, elapsed, true, null);
    }

    public static PhaseResult failure(String name, Duration elapsed, String message) {
        return new PhaseResult(name, elapsed, false, message);
    }

    public String getName()      { return name; }
    public Duration getElapsed() { return elapsed; }
    public boolean isSucceeded() { return succeeded; }
    public String getMessage()   { return message; }
}
