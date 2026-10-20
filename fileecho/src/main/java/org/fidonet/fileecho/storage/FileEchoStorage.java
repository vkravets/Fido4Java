package org.fidonet.fileecho.storage;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/** Typed persistence operations; authorization and filesystem policy belong to FileEcho. */
public interface FileEchoStorage extends AutoCloseable {
    List<FileArea> areas() throws IOException;

    FileArea area(String name) throws IOException;

    List<FileAccess> access() throws IOException;

    List<FileAccess> access(String peer) throws IOException;

    FileAccess access(String area, String peer) throws IOException;

    List<FileCreationPolicy> policies() throws IOException;

    FileCreationPolicy policy(String peer) throws IOException;

    /** Insert initial declarations without replacing existing edits or disabled areas. */
    void seedAreas(Collection<FileArea> areas) throws IOException;

    void saveArea(FileArea area) throws IOException;

    /** NONE removes the grant. */
    void saveAccess(String area, String peer, String access) throws IOException;

    /** NONE removes the policy. */
    void savePolicy(String peer, String masks, String access) throws IOException;

    /** Atomically add READ, promote WRITE to BOTH, or remove the subscription. */
    void subscribe(String area, String peer, boolean enabled) throws IOException;

    /**
     * Atomically create the area and its first grant if the validated policy is unchanged.
     * An existing area is returned without adding rights; callers must check its authorization.
     */
    FileArea createWithGrant(FileArea area, FileCreationPolicy expected) throws IOException;

    @Override
    void close() throws IOException;
}
