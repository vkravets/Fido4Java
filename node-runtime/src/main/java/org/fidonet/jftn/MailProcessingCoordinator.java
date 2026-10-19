package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Runs one serialized inbound, extension and outbound processing cycle. */
final class MailProcessingCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(MailProcessingCoordinator.class);
    private final JFtnConfig config;
    private final Tosser tosser;
    private final List<MailProcessingExtension> extensions;

    MailProcessingCoordinator(JFtnConfig config,
                              Tosser tosser,
                              List<MailProcessingExtension> extensions) {
        this.config = config;
        this.tosser = tosser;
        this.extensions = extensions;
    }

    void process() throws Exception {
        tosser.runFast(config.getInbound());
        for (Link link : config.getConfiguredLinks()) {
            Path directory = Path.of(config.getInbound(),
                                     "peers",
                                     ReceivedFile.directoryName(link.getAddr()));
            if (Files.isDirectory(directory)) {
                tosser.runFast(directory.toString(), link.getAddr());
            }
        }
        runExtensions(true);
        tosser.exportPendingNetmail();
        tosser.exportEchomail();
        runExtensions(false);
    }

    private void runExtensions(boolean beforeExport) {
        for (MailProcessingExtension extension : extensions) {
            runExtension(extension, beforeExport);
        }
    }

    private void runExtension(MailProcessingExtension extension, boolean beforeExport) {
        try {
            if (beforeExport) {
                extension.beforeExport(tosser);
            } else {
                extension.afterExport();
            }
        } catch (IOException e) {
            logger.warn("Mail extension {} deferred during {} export",
                        extension.getClass().getName(),
                        beforeExport ? "before" : "after",
                        e);
        }
    }

}
