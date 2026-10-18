package org.fidonet.jftn.script;

import org.fidonet.types.FTNAddr;

import java.nio.file.Path;
import java.util.List;

/** Validated file delivery metadata, or a bounded diagnostic without TIC secrets. */
public final class FileEchoEvent {
    public final String status;
    public final String area;
    public final Path file;
    public final String name;
    public final FTNAddr peer;
    public final String receipt;
    public final List<String> descriptions;
    public final String detail;

    public FileEchoEvent(String status,
                         String area,
                         Path file,
                         String name,
                         FTNAddr peer,
                         String receipt,
                         List<String> descriptions,
                         String detail) {
        this.status = status;
        this.area = area;
        this.file = file;
        this.name = name;
        this.peer = new FTNAddr(peer.toString());
        this.receipt = receipt;
        this.descriptions = List.copyOf(descriptions);
        this.detail = detail;
    }
}
