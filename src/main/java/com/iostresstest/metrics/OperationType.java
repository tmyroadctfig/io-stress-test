package com.iostresstest.metrics;

public enum OperationType {
    SEQ_READ("Sequential Read", true),
    RAND_READ("Random Read",    true),
    DIR_LIST("Dir Listing",     false),
    FILE_WRITE("Write",         true),
    FILE_META("Metadata",       false);

    private final String displayName;
    private final boolean hasThroughput;

    OperationType(String displayName, boolean hasThroughput) {
        this.displayName = displayName;
        this.hasThroughput = hasThroughput;
    }

    public String getDisplayName()  { return displayName; }
    public boolean hasThroughput()  { return hasThroughput; }
}
