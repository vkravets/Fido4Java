package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;

import java.util.Optional;

/** Creates a service-owned extension; an empty result means it is disabled. */
public interface MailExtensionFactory {
    Optional<MailProcessingExtension> create(JFtnConfig config, MailDatabaseFacade base);
}
