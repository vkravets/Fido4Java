package org.fidonet.jftn.tosser.jam;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.IBase;
import org.fidonet.echobase.MessageIteration;
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
            exportArea(binding);
        }
    }

    private void prepareArea(Binding binding) throws IOException {
        Files.createDirectories(binding.area.getPath().getParent());
        binding.area.ensureExists();
    }

    private void exportArea(Binding binding) throws IOException {
        if (binding.name != null && !base.isAreaExists(binding.name)) {
            throw new IOException("Unknown JAM echo area: " + binding.name);
        }
        prepareArea(binding);
        JamState state = state(binding);
        binding.area.locked(access -> {
            exportAreaMessages(binding, access, state);
            return null;
        });
    }

    private void exportAreaMessages(Binding binding, JamArea.Access access, JamState state)
            throws IOException {
        Set<String> existing = existingIdentities(access);
        if (binding.name == null) {
            for (Message message : netmail.getEditorNetmail(binding.aka)) {
                export(message, access, existing, state);
            }
            return;
        }
        MessageIteration.forEach(base.getMessages(binding.name),
                                 message -> export(message, access, existing, state));
    }

    private Set<String> existingIdentities(JamArea.Access access) throws IOException {
        Set<String> existing = new HashSet<>();
        for (JamArea.Entry entry : access.read()) {
            existing.add(MailIdentity.of(entry.getMessage()));
        }
        return existing;
    }

    /** Only local unsent posts are submissions. Ordinary stored mail is never submitted. */
    public void importOutgoing(Tosser tosser) throws IOException {
        for (Binding binding : bindings) {
            importAreaOutgoing(binding, tosser);
        }
    }

    private void importAreaOutgoing(Binding binding, Tosser tosser) throws IOException {
        prepareArea(binding);
        JamState state = state(binding);
        binding.area.locked(access -> {
            importEntries(binding, access, state, tosser);
            return null;
        });
    }

    private void importEntries(Binding binding,
                               JamArea.Access access,
                               JamState state,
                               Tosser tosser)
            throws IOException {
        for (JamArea.Entry entry : access.read()) {
            if (isOutgoing(entry)) {
                importEntrySafely(tosser, binding, access, entry, state);
            }
        }
    }

    private boolean isOutgoing(JamArea.Entry entry) {
        return (entry.getAttributes()
                & (JamArea.LOCAL | JamArea.SENT | JamArea.TYPE_LOCAL)) == JamArea.LOCAL;
    }

    private void importEntrySafely(Tosser tosser,
                                   Binding binding,
                                   JamArea.Access access,
                                   JamArea.Entry entry,
                                   JamState state)
            throws IOException {
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

    private void importOutgoing(Tosser tosser,
                                Binding binding,
                                JamArea.Access access,
                                JamArea.Entry entry,
                                JamState state)
            throws IOException {
        Message message = entry.getMessage();
        if ((entry.getAttributes() & 0xf000) != 0) {
            throw new UnsupportedOperationException("JAM file requests and attachment operations are unsupported");
        }
        if ((entry.getAttributes() & JamArea.LOCKED) != 0 && (message.getMsgId() == null
                || state.get("import." + MailIdentity.of(message)) == null)) {
            return;
        }
        if (!message.hasValidAddressing()
                || !config.getLocalAddresses().contains(message.getFAddr())) {
            throw new IllegalArgumentException("Outgoing JAM message requires valid addresses and a local sender AKA");
        }
        if (binding.name != null && !base.isAreaExists(binding.name)) {
            throw new IllegalArgumentException("Unknown echo area " + binding.name);
        }
        message = ensureMsgId(access, entry, message);
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
            ensureLocked(access, entry);
            return;
        }
        if (previous == null && (state.get("export." + identity) != null || base.isDupe(message))) {
            throw new IllegalStateException("This MSGID already exists; edited mail is not automatically resent");
        }
        state.prepare(identity, fingerprint, deliveryTargets(tosser, message, state, identity));
        access.attributes(entry.getNumber(), JamArea.LOCKED, 0);
        if (binding.name == null) {
            tosser.submitNetmail(message, "jam:" + identity);
        } else {
            tosser.submitEchomail(message);
        }
        state.put("stage." + identity, "ACCEPTED");
        access.attributes(entry.getNumber(), JamArea.LOCKED, 0);
        logger.info("Accepted JAM submission {} from {}", identity, binding.area.getPath());
    }

    private Message ensureMsgId(JamArea.Access access, JamArea.Entry entry, Message message)
            throws IOException {
        if (message.getMsgId() != null) {
            return message;
        }
        MessageIds.ensure(message, Path.of(config.getTmpDir()));
        access.assignMsgId(entry.getNumber(),
                           message.getMsgId(),
                           CharsetTools.charsetDetect(message.getMessageCharset()));
        for (JamArea.Entry updated : access.read()) {
            if (updated.getNumber() == entry.getNumber()) {
                return updated.getMessage();
            }
        }
        return message;
    }

    private void ensureLocked(JamArea.Access access, JamArea.Entry entry) throws IOException {
        if ((entry.getAttributes() & JamArea.LOCKED) == 0) {
            access.attributes(entry.getNumber(), JamArea.LOCKED, 0);
        }
    }

    private String deliveryTargets(Tosser tosser, Message message, JamState state, String identity)
            throws IOException {
        String saved = state.get("targets." + identity);
        if (saved != null) {
            return saved;
        }
        List<String> targets = new ArrayList<>();
        for (FTNAddr address : tosser.submissionTargets(message)) {
            targets.add(address.toString());
        }
        return String.join(",", targets);
    }

    public void reconcileDeliveries() throws IOException {
        OutboundSpool spool = new OutboundSpool(config);
        for (Binding binding : bindings) {
            reconcileArea(binding, spool);
        }
    }

    private void reconcileArea(Binding binding, OutboundSpool spool) throws IOException {
        JamState state = state(binding);
        binding.area.locked(access -> {
            for (JamArea.Entry entry : access.read()) {
                reconcileEntry(binding, access, entry, state, spool);
            }
            return null;
        });
    }

    private void reconcileEntry(Binding binding,
                                JamArea.Access access,
                                JamArea.Entry entry,
                                JamState state,
                                OutboundSpool spool)
            throws IOException {
        Message message = entry.getMessage();
        String identity = MailIdentity.of(message);
        String stage = state.get("stage." + identity);
        if (!"ACCEPTED".equals(stage) && !"DELIVERED".equals(stage)) {
            return;
        }
        if (!JamFingerprint.of(message, entry.getAttributes())
                           .equals(state.get("import." + identity))) {
            logger.error("JAM message {} changed while queued; delivery flags were retained",
                         identity);
            return;
        }
        if (!allAcknowledged(binding, state, spool, identity)) {
            return;
        }
        if (!"DELIVERED".equals(stage)) {
            state.put("stage." + identity, "DELIVERED");
        }
        if ((entry.getAttributes() & JamArea.SENT) == 0) {
            access.attributes(entry.getNumber(), JamArea.SENT, JamArea.LOCKED);
        }
    }

    private boolean allAcknowledged(Binding binding,
                                    JamState state,
                                    OutboundSpool spool,
                                    String identity)
            throws IOException {
        String targets = state.get("targets." + identity);
        if (targets == null) {
            throw new IOException("Missing JAM delivery targets for " + identity);
        }
        boolean delivered = true;
        for (String target : targets.isEmpty() ? new String[0] : targets.split(",")) {
            FTNAddr address = new FTNAddr(target);
            String key = binding.name == null
                    ? "netmail:jam:" + identity
                    : "echo:" + binding.name + ":" + identity + ":" + address;
            delivered &= spool.isAcknowledged(key, address);
        }
        return delivered;
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
