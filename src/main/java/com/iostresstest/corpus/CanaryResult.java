package com.iostresstest.corpus;

import java.nio.file.Path;

public class CanaryResult {

    public enum Status { OK, MISSING, ALTERED }

    private final Path file;
    private final Status status;
    private final String detail; // nullable — contains observed content if ALTERED

    public CanaryResult(Path file, Status status, String detail) {
        this.file   = file;
        this.status = status;
        this.detail = detail;
    }

    public Path getFile()    { return file; }
    public Status getStatus(){ return status; }
    public String getDetail(){ return detail; }
    public boolean isOk()    { return status == Status.OK; }
}
