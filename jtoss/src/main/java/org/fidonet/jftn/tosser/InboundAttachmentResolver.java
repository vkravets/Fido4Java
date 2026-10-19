package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Resolves an exact attachment name only inside a trusted transport peer's received files. */
public interface InboundAttachmentResolver {
    List<Path> find(JFtnConfig config, FTNAddr peer, String name) throws IOException;
}
