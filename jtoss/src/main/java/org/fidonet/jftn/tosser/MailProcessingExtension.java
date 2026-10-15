package org.fidonet.jftn.tosser;

import java.io.IOException;

/** Optional work executed under the mail service's processing lock. */
public interface MailProcessingExtension extends AutoCloseable {
    void beforeExport(Tosser tosser) throws IOException;

    void afterExport() throws IOException;

    @Override
    default void close() throws IOException {
    }
}
