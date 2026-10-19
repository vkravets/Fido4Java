package org.fidonet.mail.files;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.MessageIteration;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.misc.AtomicFiles;
import org.fidonet.packet.MailIdentity;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Primary JAM mailboxes with a separate durable delivery journal, never a SQL mirror. */
public final class JamMailStore extends FileMailStore implements MailProcessingExtension {
    private final Path jamRoot;
    private final Path editorRoot;
    private final FTNAddr aka;
    private final Charset charset;
    private final Map<String, JamArea> bindings = new LinkedHashMap<>();
    private final Map<String, FTNAddr> bindingAkas = new LinkedHashMap<>();
    private boolean scanning;

    public JamMailStore(JFtnConfig config) throws IOException {
        super(config);
        try {
            if (config.getLocalAddresses().isEmpty()) {
                throw new IOException("Legacy requires a configured local AKA");
            }
            aka = config.getLocalAddresses().iterator().next();
            charset = Charset.forName(config.getValue("JamCharset", "CP866"));
            jamRoot = Path.of(config.getValue("JamRoot", config.getEchoPath()))
                          .toAbsolutePath()
                          .normalize();
            editorRoot = Path.of(config.getValue("EditorConfigPath", "editor"))
                             .toAbsolutePath()
                             .normalize();
            configureBindings();
            recoverMailboxes();
            generateEditorFiles();
        } catch (IOException | RuntimeException failure) {
            super.close();
            throw failure;
        }
    }

    public JamArea mailbox(String area) {
        JamArea configured = bindings.get(area == null ? "netmail" : "echo:" + areaName(area));
        if (configured != null) {
            return configured;
        }
        return new JamArea(jamRoot.resolve(area == null ? "netmail" : "echo-" + areaName(area)),
                           area,
                           aka,
                           charset);
    }

    private void configureBindings() throws IOException {
        List<String> echoes = config.getValuesAsList("JamEcho");
        if (echoes != null) {
            for (String value : echoes) {
                String[] fields = value.split(",", -1);
                if (fields.length < 2 || fields.length > 3) {
                    throw new IllegalArgumentException("Expected JamEcho area,path[,AKA]");
                }
                String area = areaName(fields[0].trim());
                bind(area, fields[1].trim(), fields.length == 3 ? fields[2].trim() : null);
            }
        }
        String netmail = config.getValue("JamNetmail");
        if (netmail != null) {
            String[] fields = netmail.split(",", -1);
            if (fields.length < 1 || fields.length > 2) {
                throw new IllegalArgumentException("Expected JamNetmail path[,AKA]");
            }
            bind(null, fields[0].trim(), fields.length == 2 ? fields[1].trim() : null);
        }
        Set<Path> paths = new HashSet<>();
        Properties layout = new Properties();
        layout.setProperty("root", jamRoot.toString());
        layout.setProperty("charset", charset.name());
        layout.setProperty("aka", aka.toString());
        for (Map.Entry<String, JamArea> entry : bindings.entrySet()) {
            if (!paths.add(entry.getValue().getPath())) {
                throw new IOException("Different JAM bindings cannot share a base path");
            }
            layout.setProperty(entry.getKey(),
                               entry.getValue().getPath() + "|" + bindingAkas.get(entry.getKey()));
        }
        Path path = root.resolve("jam-layout.properties");
        Properties previous = FileRecords.read(path);
        previous.remove("format");
        if (!previous.isEmpty()
                && previous.stringPropertyNames()
                           .stream()
                           .anyMatch(key -> !previous.getProperty(key)
                                                     .equals(layout.getProperty(key)))) {
            throw new IOException("JAM layout changed; migrate existing mail before changing paths or encoding");
        }
        paths.clear();
        List<String> names = new ArrayList<>(getAreas());
        for (String key : bindings.keySet()) {
            if (key.startsWith("echo:") && !names.contains(key.substring(5))) {
                names.add(key.substring(5));
            }
        }
        names.add(null);
        for (String name : names) {
            if (!paths.add(mailbox(name).getPath())) {
                throw new IOException("Different JAM areas cannot share a base path");
            }
        }
        FileRecords.write(path, layout);
        for (String key : bindings.keySet()) {
            if (key.startsWith("echo:")) {
                createArea(key.substring(5));
            }
        }
    }

    private void bind(String area, String path, String address) throws IOException {
        FTNAddr selected = address == null ? aka : new FTNAddr(address);
        if (path.isBlank() || !config.getLocalAddresses().contains(selected)) {
            throw new IllegalArgumentException("JAM binding requires a path and a local AKA");
        }
        String key = area == null ? "netmail" : "echo:" + area;
        Path selectedPath = Path.of(path).toAbsolutePath().normalize();
        Path original = jamRoot.resolve(area == null ? "netmail" : "echo-" + area);
        if (!selectedPath.equals(original) && Files.exists(Path.of(original + ".jhr"))) {
            String recorded = FileRecords.read(root.resolve("jam-layout.properties"))
                                         .getProperty(key);
            if (!(selectedPath + "|" + selected).equals(recorded)) {
                throw new IOException("Existing JAM area requires migration before changing its path: "
                        + area);
            }
        }
        bindingAkas.put(key, selected);
        if (bindings.putIfAbsent(key, new JamArea(selectedPath, area, selected, charset)) != null) {
            throw new IllegalArgumentException("Duplicate JAM area binding");
        }
    }

    private void ensureArea(JamArea area) throws IOException {
        Files.createDirectories(area.getPath().getParent());
        area.ensureExists();
    }

    private void recoverMailboxes() throws IOException {
        for (Path path : FileRecords.list(root.resolve("jam-publications"))) {
            String base = FileRecords.read(path).getProperty("path");
            if (base == null) {
                throw new IOException("Invalid JAM publication receipt");
            }
            for (String suffix : List.of(".jhr", ".jdt", ".jdx")) {
                if (!Files.isRegularFile(Path.of(base + suffix))) {
                    throw new IOException("Primary JAM files are missing; restore the mailbox backup: "
                            + base);
                }
            }
        }
        for (String kind : List.of("echo", "netmail")) {
            for (Path path : FileRecords.list(root.resolve(kind))) {
                Properties record = FileRecords.read(path);
                if (record.containsKey("body")) {
                    Message message = MailRecord.get(record);
                    appendIfAbsent(message, record.getProperty("identity"));
                    if (message.isEchomail()) {
                        compactEcho(message);
                    }
                }
            }
        }
    }

    @Override
    public synchronized void addMessage(Message message, String area) {
        super.addMessage(message, area);
        mirrorAccepted(message);
        compactEcho(message);
    }

    @Override
    public synchronized void saveNetmail(Message message, String key, String nextHop) {
        super.saveNetmail(message, key, nextHop);
        mirrorAccepted(message);
    }

    private void mirrorAccepted(Message message) {
        if (scanning) {
            return;
        }
        try {
            appendIfAbsent(message);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void appendIfAbsent(Message message) throws IOException {
        appendIfAbsent(message, MailIdentity.of(message));
    }

    private void appendIfAbsent(Message message, String identity) throws IOException {
        if (identity == null || !identity.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid JAM publication identity");
        }
        Path publication = publication(message, identity);
        if (Files.exists(publication)) {
            return;
        }
        JamArea area = mailbox(message.isEchomail() ? message.getArea() : null);
        ensureArea(area);
        area.locked(access -> {
            boolean[] found = {false};
            access.forEach(entry -> found[0] |= MailIdentity.of(entry.getMessage())
                                                            .equals(MailIdentity.of(message)));
            if (!found[0]) {
                int attributes = JamArea.attributesOf(message);
                attributes = message.getUpLink() == null
                        ? attributes | JamArea.LOCAL | JamArea.SENT
                        : attributes & ~JamArea.LOCAL;
                access.append(message, attributes);
            }
            recordPublication(message, identity);
            return null;
        });
    }

    private Path publication(Message message, String identity) {
        return root.resolve("jam-publications")
                   .resolve(FileRecords.key((message.isEchomail()
                           ? areaName(message.getArea())
                           : "netmail") + ":" + identity) + ".properties");
    }

    private void recordPublication(Message message) {
        recordPublication(message, MailIdentity.of(message));
    }

    private void recordPublication(Message message, String identity) {
        Properties record = new Properties();
        record.setProperty("path",
                           mailbox(message.isEchomail() ? message.getArea() : null).getPath()
                                                                                   .toString());
        FileRecords.write(publication(message, identity), record);
    }

    @Override
    protected boolean needsJamPublication() {
        return true;
    }

    @Override
    public synchronized void beforeExport(Tosser tosser) throws IOException {
        requireOpen();
        scan(mailbox(null), tosser);
        for (String area : getAreas()) {
            scan(mailbox(area), tosser);
        }
        generateEditorFiles();
    }

    @Override
    public void afterExport() {
        super.afterExport();
    }

    private void scan(JamArea area, Tosser tosser) throws IOException {
        ensureArea(area);
        area.locked(access -> {
            access.forEach(attributes -> (attributes & (JamArea.LOCAL | JamArea.SENT
                    | JamArea.TYPE_LOCAL | JamArea.LOCKED | JamArea.DELETED)) == JamArea.LOCAL,
                           entry -> submit(access, entry, tosser));
            return null;
        });
    }

    private void submit(JamArea.Access access, JamArea.Entry entry, Tosser tosser)
            throws IOException {
        Message message = entry.getMessage();
        if (!message.hasValidAddressing()
                || !config.getLocalAddresses().contains(message.getFAddr())) {
            throw new IOException("JAM submission requires a local sender AKA and valid addressing");
        }
        if (message.getMsgId() == null) {
            tosser.assignMessageId(message);
            access.assignMsgId(entry.getNumber(),
                               message.getMsgId(),
                               CharsetTools.charsetDetect(message.getMessageCharset()));
            message = access.get(entry.getNumber()).getMessage();
        }
        String identity = MailIdentity.of(message);
        recordPublication(message);
        Path path = root.resolve("submissions").resolve(FileRecords.key(identity) + ".properties");
        Properties receipt = FileRecords.read(path);
        String fingerprint = FileRecords.key(message.getFrom()
                                             + "\n"
                                             + message.getTo()
                                             + "\n"
                                             + message.getSubject()
                                             + "\n"
                                             + message.getBody()
                                             + "\n"
                                             + message.getAttributes());
        if (!receipt.isEmpty() && !fingerprint.equals(receipt.getProperty("fingerprint"))) {
            throw new IOException("JAM message was edited after submission; create a new message");
        }
        if (!"ACCEPTED".equals(receipt.getProperty("state"))) {
            receipt.setProperty("fingerprint", fingerprint);
            FileRecords.write(path, receipt);
            submitToQueue(message, identity, tosser);
            receipt.setProperty("state", "ACCEPTED");
            FileRecords.write(path, receipt);
        }
        // Queue publication precedes Snt. A restart reuses the same receipt/delivery key.
        access.attributes(entry.getNumber(), JamArea.SENT, 0);
    }

    private void submitToQueue(Message message, String identity, Tosser tosser) throws IOException {
        scanning = true;
        try {
            message.setUpLink(null);
            if (message.isEchomail()) {
                tosser.submitEchomail(message);
            } else {
                tosser.submitNetmail(message, "jam:" + identity);
            }
        } finally {
            scanning = false;
        }
    }

    @Override
    public Iterator<Message> getMessages(String area) {
        try {
            List<Message> result = new ArrayList<>();
            ensureArea(mailbox(area));
            mailbox(area).visitReadOnly(entry -> result.add(entry.getMessage()));
            return result.iterator();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public long getMessageSize(String area) {
        long count = 0;
        Iterator<Message> messages = getMessages(area);
        while (messages.hasNext()) {
            messages.next();
            count++;
        }
        return count;
    }

    @Override
    public void visitRecentEcho(String area, int count, MessageIteration.Handler handler)
            throws IOException {
        List<JamArea.Entry> entries = mailbox(area).readOnly();
        for (int index = Math.max(0, entries.size() - count); index < entries.size(); index++) {
            handler.accept(entries.get(index).getMessage());
        }
    }

    @Override
    public void visitEchoRange(String area, long first, long last, MessageIteration.Handler handler)
            throws IOException {
        mailbox(area).visitReadOnly(entry -> {
            if (entry.getNumber() >= first && entry.getNumber() <= last) {
                handler.accept(entry.getMessage());
            }
        });
    }

    @Override
    public List<Message> getLocalNetmail(FTNAddr address) {
        try {
            List<Message> result = new ArrayList<>();
            mailbox(null).visitReadOnly(entry -> {
                if (address.equals(entry.getMessage().getTAddr())) {
                    result.add(entry.getMessage());
                }
            });
            return result;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void generateEditorFiles() throws IOException {
        Files.createDirectories(editorRoot);
        StringBuilder golded = new StringBuilder("; Generated by JFtn. Edit golded.cfg instead.\n");
        StringBuilder husky = new StringBuilder("# Generated editor area catalog; not a complete HPT configuration.\n");
        for (FTNAddr local : config.getLocalAddresses()) {
            husky.append("Address ").append(local).append('\n');
        }
        editorArea(golded, husky, null);
        for (String area : getAreas()) {
            editorArea(golded, husky, area);
        }
        AtomicFiles.replace(editorRoot.resolve("areas.lst"),
                            output -> output.write(golded.toString()
                                                         .getBytes(StandardCharsets.UTF_8)));
        AtomicFiles.replace(editorRoot.resolve("fidoconfig.areas"),
                            output -> output.write(husky.toString()
                                                        .getBytes(StandardCharsets.UTF_8)));
        Path example = editorRoot.resolve("golded.cfg.example");
        if (!Files.exists(example)) {
            String text = "; Copy to golded.cfg and customize. Generated areas use AREADEF syntax.\n"
                    + "Username " + config.getValue("SysOp") + "\nAddress " + aka + "\nInclude "
                    + quoted(editorRoot.resolve("areas.lst").toString()) + "\n";
            AtomicFiles.publish(example,
                                output -> output.write(text.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void editorArea(StringBuilder golded, StringBuilder husky, String area)
            throws IOException {
        JamArea mailbox = mailbox(area);
        ensureArea(mailbox);
        String name = area == null ? "NETMAIL" : area;
        String path = quoted(mailbox.getPath().toString());
        FTNAddr selected = bindingAkas.getOrDefault(area == null ? "netmail" : "echo:" + area, aka);
        golded.append("AREADEF ")
              .append(name)
              .append(" \"")
              .append(name)
              .append("\" 0 ")
              .append(area == null ? "Net" : "Echo")
              .append(" JAM ")
              .append(path)
              .append(' ')
              .append(selected)
              .append(area == null ? " (Loc Pvt)\n" : " (Loc)\n");
        husky.append(area == null ? "NetmailArea " : "EchoArea ")
             .append(name)
             .append(' ')
             .append(path)
             .append(" -b Jam -a ")
             .append(selected)
             .append('\n');
    }

    private String quoted(String value) {
        if (value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("Editor paths cannot contain quotes or newlines");
        }
        return "\"" + value + "\"";
    }
}
