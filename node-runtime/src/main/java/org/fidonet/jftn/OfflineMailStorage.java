package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.config.StorageConfiguration;
import org.fidonet.echobase.MailRepository;
import org.fidonet.echobase.MailStorage;
import org.fidonet.storage.StorageSession;

import java.io.IOException;
import java.nio.file.Path;

/** Offline operations honor the same endpoint as the running database plugin. */
final class OfflineMailStorage {
    private static final Path DEFAULT = Path.of("./database/jftn10");

    private OfflineMailStorage() {
    }

    static StorageSession open(JFtnConfig config, boolean existing) throws IOException {
        if (existing && !StorageConfiguration.exists(config, "mail", DEFAULT)) {
            throw new IOException("Mail storage does not exist; check the configuration and working directory");
        }
        return StorageConfiguration.open(config, "mail", DEFAULT);
    }

    static MailRepository repository(StorageSession storage) throws IOException {
        MailStorage mail = storage.require(MailStorage.class);
        if (!(mail instanceof MailRepository)) {
            throw new IOException("The selected mail storage lacks administrative repository capabilities");
        }
        return (MailRepository) mail;
    }
}
