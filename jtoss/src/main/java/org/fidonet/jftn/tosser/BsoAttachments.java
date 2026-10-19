package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.misc.FileDigests;
import org.fidonet.packet.AttachmentNames;
import org.fidonet.types.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/** Local attaches are confined to an explicit root; transit attaches use only their receiving peer. */
final class BsoAttachments {
    private BsoAttachments() {
    }

    static List<BsoOutbound.File> sources(JFtnConfig config, Message message) throws IOException {
        List<BsoOutbound.File> result = new ArrayList<>();
        if ((message.getAttributes() & 16) == 0) {
            return result;
        }
        if ((message.getAttributes() & 32) != 0 && message.getUpLink() == null) {
            throw new IOException("Transit attachment has no authenticated source receipt");
        }
        for (String token : AttachmentNames.tokens(message.getSubject())) {
            String name = AttachmentNames.basename(token);
            Path source = message.getUpLink() == null
                    ? local(config, token)
                    : received(config, message, name);
            result.add(new BsoOutbound.File(source, name, action(config, message)));
        }
        return result;
    }

    private static Path local(JFtnConfig config, String token) throws IOException {
        Path root = Path.of(config.getValue("FileAttachRoot",
                                            Path.of(config.getOutbound(), "attachments")
                                                .toString()))
                        .toAbsolutePath()
                        .normalize()
                        .toRealPath();
        Path candidate = root.resolve(token).normalize();
        if (!candidate.startsWith(root)
                || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                || !candidate.toRealPath().startsWith(root)) {
            throw new IOException("Local attachment must be a regular file beneath FileAttachRoot");
        }
        validate(candidate);
        return candidate;
    }

    private static Path received(JFtnConfig config, Message message, String name)
            throws IOException {
        List<Path> matches = new ArrayList<>();
        for (InboundAttachmentResolver resolver : ServiceLoader.load(InboundAttachmentResolver.class)) {
            matches.addAll(resolver.find(config, message.getUpLink(), name));
        }
        if (matches.isEmpty()) {
            throw new IOException("Attachment has not arrived from the trusted peer: " + name);
        }
        Path first = matches.get(0);
        byte[] expected = digest(first);
        for (Path match : matches) {
            if (!Arrays.equals(expected, digest(match))) {
                throw new IOException("Ambiguous received attachment name: " + name);
            }
        }
        return first;
    }

    private static void validate(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) > 128L * 1024 * 1024) {
            throw new IOException("Attachment must be a regular file of at most 128 MiB");
        }
    }

    private static byte[] digest(Path path) throws IOException {
        return FileDigests.sha256(path, 128L * 1024 * 1024);
    }

    private static BsoOutbound.Action action(JFtnConfig config, Message message) {
        String keep = config.getValue("BsoKeepTransitFiles", "true");
        if (!Set.of("true", "false").contains(keep)) {
            throw new IllegalArgumentException("BsoKeepTransitFiles must be true or false");
        }
        String flags = " " + NetmailRouter.configFlags(message) + " ";
        if (flags.contains(" KFS ") || message.getUpLink() != null && !Boolean.parseBoolean(keep)) {
            return BsoOutbound.Action.DELETE;
        }
        return flags.contains(" TFS ") ? BsoOutbound.Action.TRUNCATE : BsoOutbound.Action.KEEP;
    }
}
