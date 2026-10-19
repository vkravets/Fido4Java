package org.fidonet.jftn;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.RuntimeProfile;
import org.fidonet.echobase.MailStorage;
import org.fidonet.jftn.poll.PollService;

import java.nio.file.Files;
import java.nio.file.Path;

/** Optional browser lifecycle, isolated from the transport and SQL-free runner. */
public interface ProfileServices {
    default void prepare(Path configuration) throws Exception {
        if (!Files.isRegularFile(configuration)) {
            throw new IllegalArgumentException("Create jftn.conf from jftn.conf.example before starting a file-managed profile");
        }
    }

    default AutoCloseable open(JFtnConfig config,
                               MailStorage storage,
                               MailRuntime mail,
                               SessionContext context,
                               PollService polls)
            throws Exception {
        return null;
    }

    static ProfileServices forProfile(RuntimeProfile profile) throws ReflectiveOperationException {
        if (profile.hasUi()) {
            return (ProfileServices) Class.forName("org.fidonet.jftn.FullProfileServices")
                                          .getConstructor()
                                          .newInstance();
        }
        return new ProfileServices() {
        };
    }
}
