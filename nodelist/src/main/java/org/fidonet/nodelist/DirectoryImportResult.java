package org.fidonet.nodelist;

import org.fidonet.jftn.script.DirectoryImportService;

public final class DirectoryImportResult implements DirectoryImportService.Result {
    private final String source;
    private final long records;
    private final boolean changed;
    private final String fingerprint;
    private final String header;

    public DirectoryImportResult(String source,
                                 long records,
                                 boolean changed,
                                 String fingerprint,
                                 String header) {
        this.source = source;
        this.records = records;
        this.changed = changed;
        this.fingerprint = fingerprint;
        this.header = header;
    }

    public String getSource() {
        return source;
    }

    public long getRecords() {
        return records;
    }

    public boolean isChanged() {
        return changed;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getHeader() {
        return header;
    }
}
