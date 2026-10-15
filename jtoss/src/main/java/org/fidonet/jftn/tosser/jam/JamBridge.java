package org.fidonet.jftn.tosser.jam;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.IBase;
import org.fidonet.echobase.NetmailStore;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.jftn.tosser.MessageIds;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.packet.MailIdentity;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** SQL is authoritative; JAM is a persistent editor view, never a disposable snapshot. */
public final class JamBridge {
    private static final Logger logger = LoggerFactory.getLogger(JamBridge.class);
    private final JFtnConfig config;
    private final IBase base;
    private final NetmailStore netmail;
    private final List<Binding> bindings = new ArrayList<>();

    public JamBridge(JFtnConfig config, IBase base, NetmailStore netmail) {
        this.config = config;
        this.base = base;
        this.netmail = netmail;
        Set<Path> paths = new HashSet<>();
        for (String declaration : declarations("JamEcho")) {
            String[] fields = declaration.split(",", 3);
            if (fields.length < 2 || fields[0].trim().isEmpty()) {
                throw new IllegalArgumentException("JamEcho requires AREA,path[,AKA]");
            }
            addBinding(fields[0].trim().toLowerCase(Locale.ROOT),
                       fields[1].trim(),
                       fields.length == 3 ? fields[2].trim() : null,
                       paths);
        }
        for (String declaration : declarations("JamNetmail")) {
            String[] fields = declaration.split(",", 2);
            addBinding(null, fields[0].trim(), fields.length == 2 ? fields[1].trim() : null, paths);
        }
    }

    private List<String> declarations(String key) {
        List<String> values = config.getValuesAsList(key);
        return values == null ? Collections.emptyList() : values;
    }

    private void addBinding(String name, String path, String address, Set<Path> paths) {
        if (path.isEmpty() || config.getLocalAddresses().isEmpty()) {
            throw new IllegalArgumentException("JAM requires a path and a configured local AKA");
        }
        FTNAddr aka = address == null
                ? config.getLocalAddresses().iterator().next()
                : new FTNAddr(address);
        if (!config.getLocalAddresses().contains(aka)) {
            throw new IllegalArgumentException("JAM AKA must be local: " + aka);
        }
        Path normalized = Path.of(path).toAbsolutePath().normalize();
        if (!paths.add(normalized)) {
            throw new IllegalArgumentException("Duplicate JAM path: " + normalized);
        }
        bindings.add(new Binding(name,
                                 normalized,
                                 aka,
                                 Charset.forName(config.getValue("JamCharset", "CP866"))));
    }

    public void exportMessages() throws IOException {
        for (Binding binding : bindings) {
            if (binding.name != null && !base.isAreaExists(binding.name)) {
                throw new IOException("Unknown JAM echo area: " + binding.name);
            }
            Files.createDirectories(binding.area.getPath().getParent());
            binding.area.ensureExists();
            JamState state = state(binding);
            binding.area.locked(access -> {
                Set<String> existing = new HashSet<>();
                for (JamArea.Entry entry : access.read()) {
                    existing.add(MailIdentity.of(entry.getMessage()));
                }
                if (binding.name == null) {
                    for (Message message : netmail.getEditorNetmail(binding.aka)) {
                        export(message, access, existing, state);
                    }
                } else {
                    Iterator<Message> messages = base.getMessages(binding.name);
                    try {
                        while (messages.hasNext()) {
                            export(messages.next(), access, existing, state);
                        }
                    } finally {
                        close(messages);
                    }
                }
                return null;
            });
        }
    }

    /** Only local unsent posts are submissions. Ordinary stored mail is never submitted. */
    public void importOutgoing(Tosser tosser) throws IOException {
        for (Binding binding : bindings) {
            Files.createDirectories(binding.area.getPath().getParent());
            binding.area.ensureExists();
            JamState state = state(binding);
            binding.area.locked(access -> {
                for (JamArea.Entry entry : access.read()) {
                    if ((entry.getAttributes() & (JamArea.LOCAL | JamArea.SENT
                            | JamArea.TYPE_LOCAL)) != JamArea.LOCAL) {
                        continue;
                    }
                    try {
                        importOutgoing(tosser, binding, access, entry, state);
                    } catch (IllegalArgumentException | IllegalStateException
                            | UnsupportedOperationException e) {
                        logger.warn("JAM submission rejected at {} message {}: {}",
                                    binding.area.getPath(),
                                    entry.getNumber(),
                                    e.getMessage());
                    }
                }
                return null;
            });
        }
    }

    private void importOutgoing(Tosser tosser,
                                Binding binding,
                                JamArea.Access access,
                                JamArea.Entry entry,
                                JamState state)
            throws IOException {
        Message message = entry.getMessage();
        if (!message.hasValidAddressing()
                || !config.getLocalAddresses().contains(message.getFAddr())) {
            throw new IllegalArgumentException("Outgoing JAM message requires valid addresses and a local sender AKA");
        }
        if (binding.name != null && !base.isAreaExists(binding.name)) {
            throw new IllegalArgumentException("Unknown echo area " + binding.name);
        }
        if (message.getMsgId() == null) {
            MessageIds.ensure(message, Path.of(config.getTmpDir()));
            access.assignMsgId(entry.getNumber(),
                               message.getMsgId(),
                               CharsetTools.charsetDetect(message.getMessageCharset()));
            for (JamArea.Entry updated : access.read()) {
                if (updated.getNumber() == entry.getNumber()) {
                    message = updated.getMessage();
                    break;
                }
            }
        }
        String identity = MailIdentity.of(message);
        String fingerprint = JamFingerprint.of(message, entry.getAttributes());
        String previous = state.get("import." + identity);
        if (previous != null && !previous.equals(fingerprint)) {
            throw new IllegalStateException("Message changed after submission; create a new message instead");
        }
        String stage = state.get("stage." + identity);
        if ("DELIVERED".equals(stage)) {
            throw new IllegalStateException("Already delivered; create a new message for resending");
        }
        if ((entry.getAttributes() & JamArea.LOCKED) != 0 && previous == null) {
            return;
        }
        if ("ACCEPTED".equals(stage)) {
            access.attributes(entry.getNumber(), JamArea.LOCKED, 0);
            return;
        }
        if (previous == null && (state.get("export." + identity) != null || base.isDupe(message))) {
            throw new IllegalStateException("This MSGID already exists; edited mail is not automatically resent");
        }
        state.put("import." + identity, fingerprint);
        if (state.get("targets." + identity) == null) {
            List<String> targets = new ArrayList<>();
            for (FTNAddr address : tosser.submissionTargets(message)) {
                targets.add(address.toString());
            }
            state.put("targets." + identity, String.join(",", targets));
        }
        state.put("stage." + identity, "PREPARED");
        if (binding.name == null) {
            tosser.submitNetmail(message, "jam:" + identity);
        } else {
            tosser.submitEchomail(message);
        }
        state.put("stage." + identity, "ACCEPTED");
        access.attributes(entry.getNumber(), JamArea.LOCKED, 0);
        logger.info("Accepted JAM submission {} from {}", identity, binding.area.getPath());
    }

    public void reconcileDeliveries() throws IOException {
        OutboundSpool spool = new OutboundSpool(config);
        for (Binding binding : bindings) {
            JamState state = state(binding);
            binding.area.locked(access -> {
                for (JamArea.Entry entry : access.read()) {
                    Message message = entry.getMessage();
                    String identity = MailIdentity.of(message);
                    String stage = state.get("stage." + identity);
                    if (!"ACCEPTED".equals(stage) && !"DELIVERED".equals(stage)) {
                        continue;
                    }
                    if (!JamFingerprint.of(message, entry.getAttributes())
                                       .equals(state.get("import." + identity))) {
                        logger.error("JAM message {} changed while queued; delivery flags were retained",
                                     identity);
                        continue;
                    }
                    String targets = state.get("targets." + identity);
                    if (targets == null) {
                        throw new IOException("Missing JAM delivery targets for " + identity);
                    }
                    boolean delivered = true;
                    for (String target : targets.isEmpty() ? new String[0] : targets.split(",")) {
                        String key = binding.name == null
                                ? "netmail:jam:" + identity
                                : "echo:" + binding.name + ":" + identity + ":"
                                        + new FTNAddr(target);
                        delivered &= spool.isAcknowledged(key, new FTNAddr(target));
                    }
                    if (delivered) {
                        state.put("stage." + identity, "DELIVERED");
                        if ((entry.getAttributes() & JamArea.SENT) == 0) {
                            access.attributes(entry.getNumber(), JamArea.SENT, JamArea.LOCKED);
                        }
                    }
                }
                return null;
            });
        }
    }

    private void export(Message message,
                        JamArea.Access access,
                        Set<String> existing,
                        JamState state)
            throws IOException {
        String identity = MailIdentity.of(message);
        if (state.get("export." + identity) != null) {
            return;
        }
        if (!existing.contains(identity)) {
            access.append(message, JamArea.attributesOf(message) & ~JamArea.LOCAL);
            existing.add(identity);
        }
        state.put("export." + identity, "true");
    }

    private JamState state(Binding binding) throws IOException {
        String id = UUID.nameUUIDFromBytes(binding.area.getPath()
                                                       .toString()
                                                       .getBytes(StandardCharsets.UTF_8))
                        .toString();
        return new JamState(Path.of(config.getTmpDir(), "jam-bridge", id + ".properties"));
    }

    private static void close(Iterator<Message> messages) throws IOException {
        if (messages instanceof AutoCloseable) {
            try {
                ((AutoCloseable) messages).close();
            } catch (Exception e) {
                throw new IOException("Unable to close SQL message iterator", e);
            }
        }
    }

    private static final class Binding {
        private final String name;
        private final FTNAddr aka;
        private final JamArea area;

        private Binding(String name, Path path, FTNAddr aka, Charset charset) {
            this.name = name;
            this.aka = aka;
            area = new JamArea(path, name, aka, charset);
        }
    }
}
