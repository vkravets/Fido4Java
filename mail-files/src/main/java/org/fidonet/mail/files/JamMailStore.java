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
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/** Primary JAM mailboxes with a separate durable delivery journal, never a SQL mirror. */
public final class JamMailStore extends FileMailStore implements MailProcessingExtension {
    private final Path jamRoot;
    private final Path editorRoot;
    private final FTNAddr aka;
    private final Charset charset;
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
            recoverMailboxes();
            generateEditorFiles();
        } catch (IOException | RuntimeException failure) {
            super.close();
            throw failure;
        }
    }

    public JamArea mailbox(String area) {
        return new JamArea(jamRoot.resolve(area == null ? "netmail" : "echo-" + areaName(area)),
                           area,
                           aka,
                           charset);
    }

    private void recoverMailboxes() throws IOException {
        for (String kind : List.of("echo", "netmail")) {
            for (Path path : FileRecords.list(root.resolve(kind))) {
                Properties record = FileRecords.read(path);
                if (record.containsKey("body")) {
                    appendIfAbsent(MailRecord.get(record));
                }
            }
        }
    }

    @Override
    public synchronized void addMessage(Message message, String area) {
        super.addMessage(message, area);
        mirrorAccepted(message);
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
        Path publication = publication(message);
        if (Files.exists(publication)) {
            return;
        }
        JamArea area = mailbox(message.isEchomail() ? message.getArea() : null);
        area.ensureExists();
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
            FileRecords.write(publication, new Properties());
            return null;
        });
    }

    private Path publication(Message message) {
        return root.resolve("jam-publications")
                   .resolve(FileRecords.key((message.isEchomail() ? message.getArea() : "netmail")
                                            + ":"
                                            + MailIdentity.of(message)) + ".properties");
    }

    @Override
    public synchronized void beforeExport(Tosser tosser) throws IOException {
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
        area.ensureExists();
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
        FileRecords.write(publication(message), new Properties());
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
            mailbox(area).ensureExists();
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
        husky.append("Address ").append(aka).append('\n');
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
                    + "Address " + aka + "\nInclude "
                    + quoted(editorRoot.resolve("areas.lst").toString()) + "\n";
            AtomicFiles.publish(example,
                                output -> output.write(text.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void editorArea(StringBuilder golded, StringBuilder husky, String area)
            throws IOException {
        JamArea mailbox = mailbox(area);
        mailbox.ensureExists();
        String name = area == null ? "NETMAIL" : area;
        String path = quoted(mailbox.getPath().toString());
        golded.append("AREADEF ")
              .append(name)
              .append(" \"")
              .append(name)
              .append("\" 0 ")
              .append(area == null ? "Net" : "Echo")
              .append(" JAM ")
              .append(path)
              .append(' ')
              .append(aka)
              .append(area == null ? " (Loc Pvt)\n" : " (Loc)\n");
        husky.append(area == null ? "NetmailArea " : "EchoArea ")
             .append(name)
             .append(' ')
             .append(path)
             .append(" -b Jam -a ")
             .append(aka)
             .append('\n');
    }

    private String quoted(String value) {
        if (value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("Editor paths cannot contain quotes or newlines");
        }
        return "\"" + value + "\"";
    }
}
