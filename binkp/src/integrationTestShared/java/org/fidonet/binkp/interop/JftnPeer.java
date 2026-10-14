package org.fidonet.binkp.interop;

import java.nio.file.Path;

public interface JftnPeer extends AutoCloseable {
    void connect(String host, int port) throws Exception;

    int listen() throws Exception;

    void awaitConnection() throws Exception;

    void assertReceivedFile() throws Exception;

    void assertSessionFinished() throws Exception;

    void assertProtocol() throws Exception;

    void saveDiagnostics(Path artifacts);
}
