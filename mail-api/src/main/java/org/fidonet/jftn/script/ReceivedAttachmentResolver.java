package org.fidonet.jftn.script;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.tosser.InboundAttachmentResolver;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ReceivedAttachmentResolver implements InboundAttachmentResolver {
    @Override
    public List<Path> find(JFtnConfig config, FTNAddr peer, String name) throws IOException {
        List<Path> matches = new ArrayList<>();
        for (ReceivedFile file : ReceivedFile.list(config)) {
            if (file.getPeer().equals(peer) && file.getName().equalsIgnoreCase(name)
                    && Files.isRegularFile(file.getPath(), LinkOption.NOFOLLOW_LINKS)) {
                matches.add(file.getPath());
            }
        }
        return matches;
    }
}
