package org.fidonet.mail.files;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.robots.FileRobotRules;
import org.fidonet.db.robots.RobotRules;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.EchoHistory;
import org.fidonet.echobase.MailStorage;
import org.fidonet.echobase.MessageIteration;
import org.fidonet.echobase.NetmailStore;
import org.fidonet.echobase.QueuedNetmail;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.packet.MailIdentity;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

/** Persistent delivery snapshots, receipts and subscriptions, without a mailbox database. */
public class FileMailStore implements MailStorage, MailProcessingExtension {
    protected final JFtnConfig config;
    protected final Path root;
    private final Properties catalog;
    private final FileStateLock ownership;

    public FileMailStore(JFtnConfig config) {
        this.config = config;
        root = Path.of(config.getValue("MailStatePath",
                                       Path.of(config.getTmpDir(), "mail-state").toString()));
        ownership = new FileStateLock(root);
        try {
            catalog = FileRecords.read(root.resolve("catalog.properties"));
            String owner = config.getValue("RuntimeProfile", "transit").toLowerCase(Locale.ROOT);
            String previous = catalog.getProperty("profile", owner);
            if (!previous.equals(owner)) {
                throw new IllegalStateException("MailStatePath belongs to another profile; migrate explicitly before switching");
            }
            catalog.setProperty("profile", owner);
            FileRecords.write(root.resolve("catalog.properties"), catalog);
        } catch (RuntimeException failure) {
            ownership.close();
            throw failure;
        }
    }

    @Override
    public boolean open() {
        return true;
    }

    protected Path echoRecord(Message message) {
        return root.resolve("echo")
                   .resolve(FileRecords.key(message.getArea()
                                                   .toLowerCase(Locale.ROOT)
                                            + ":"
                                            + MailIdentity.of(message)) + ".properties");
    }

    private Path netmailRecord(String key) {
        return root.resolve("netmail").resolve(FileRecords.key(key) + ".properties");
    }

    @Override
    public synchronized boolean createArea(String area) {
        return createArea(area, area);
    }

    @Override
    public synchronized boolean createArea(String area, String description) {
        String name = areaName(area);
        if (isAreaExists(name)) {
            return false;
        }
        catalog.setProperty("area." + name, description == null ? name : description);
        saveCatalog();
        return true;
    }

    static String areaName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                || value.contains("..")) {
            throw new IllegalArgumentException("Invalid echo area name");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public synchronized boolean createEchoFromLink(Link link, String area, AccessLevel access) {
        String name = areaName(area);
        if (isAreaExists(name)) {
            return false;
        }
        catalog.setProperty("area." + name, name);
        catalog.setProperty(grant(link, name), access.name());
        saveCatalog();
        return true;
    }

    private void saveCatalog() {
        FileRecords.write(root.resolve("catalog.properties"), catalog);
    }

    @Override
    public synchronized void configureSubscription(Link link, String area, AccessLevel access) {
        configureEchoSubscription(link, area, access, EchoHistory.ALL);
    }

    @Override
    public synchronized void configureEchoSubscription(Link link,
                                                       String area,
                                                       AccessLevel access,
                                                       EchoHistory history) {
        String name = areaName(area);
        if (!isAreaExists(name)) {
            catalog.setProperty("area." + name, name);
        }
        // Existing delivery snapshots keep their original recipients; new grants never rewrite them.
        catalog.setProperty(grant(link, name), access.name());
        saveCatalog();
    }

    private String grant(Link link, String area) {
        if (config.getLink(link.getAddr()) == null) {
            throw new IllegalArgumentException("Subscription requires a configured link");
        }
        return "grant." + link.getAddr() + ":" + areaName(area);
    }

    @Override
    public synchronized void removeEchoSubscription(Link link, String area) {
        catalog.remove(grant(link, area));
        saveCatalog();
    }

    @Override
    public void configureLink(Link link, String password) {
        if (config.getLink(link.getAddr()) == null) {
            throw new IllegalArgumentException("Unknown configured link");
        }
    }

    @Override
    public RobotRules getRobotRules() {
        return new FileRobotRules(config);
    }

    @Override
    public synchronized void addMessage(Message message, String area) {
        if (!isAreaExists(area) || !area.equalsIgnoreCase(message.getArea())) {
            throw new IllegalArgumentException("Message requires its declared echo area");
        }
        Path path = echoRecord(message);
        if (!Files.exists(path)) {
            Properties record = new Properties();
            MailRecord.put(record, message);
            record.setProperty("identity", MailIdentity.of(message));
            record.setProperty("area", areaName(area));
            List<String> readers = config.getConfiguredLinks()
                                         .stream()
                                         .filter(link -> canReadEcho(link, area))
                                         .map(link -> "outbound:" + link.getAddr())
                                         .collect(Collectors.toList());
            record.setProperty("consumers", String.join(",", readers));
            FileRecords.write(path, record);
        }
    }

    @Override
    public boolean isDupe(Message message) {
        return Files.exists(echoRecord(message));
    }

    @Override
    public synchronized void visitNewEcho(String area,
                                          String consumer,
                                          MessageIteration.Handler handler)
            throws IOException {
        for (Path path : FileRecords.list(root.resolve("echo"))) {
            Properties record = FileRecords.read(path);
            if (!area.equals(record.getProperty("area")) || !record.containsKey("body")
                    || !List.of(record.getProperty("consumers").split(",")).contains(consumer)
                    || record.containsKey("done." + consumer)) {
                continue;
            }
            handler.accept(MailRecord.get(record));
            record.setProperty("done." + consumer, "true");
            if (discardEchoPayload() && List.of(record.getProperty("consumers").split(","))
                                            .stream()
                                            .allMatch(item -> record.containsKey("done." + item))) {
                record.remove("body");
            }
            FileRecords.write(path, record);
        }
    }

    protected boolean discardEchoPayload() {
        return true;
    }

    @Override
    public synchronized void saveNetmail(Message message, String key, String nextHop) {
        saveNetmail(message, key, nextHop, false);
    }

    @Override
    public synchronized void stageNetmail(Message message, String key, String nextHop) {
        throw new UnsupportedOperationException("SQL-free submissions use the durable queue directly");
    }

    private void saveNetmail(Message message, String key, String nextHop, boolean waiting) {
        Path path = netmailRecord(key);
        if (Files.exists(path)) {
            Properties existing = FileRecords.read(path);
            if (!existing.getProperty("identity").equals(MailIdentity.of(message))) {
                throw new IllegalStateException("Delivery identity was reused for different mail");
            }
            return;
        }
        Properties record = new Properties();
        MailRecord.put(record, message);
        record.setProperty("key", key);
        record.setProperty("identity", MailIdentity.of(message));
        record.setProperty("nextHop", nextHop == null ? "" : nextHop);
        record.setProperty("state", nextHop == null ? "LOCAL" : "QUEUED");
        FileRecords.write(path, record);
    }

    @Override
    public synchronized boolean isDuplicateNetmail(Message message, String key) {
        for (Path path : FileRecords.list(root.resolve("netmail"))) {
            Properties record = FileRecords.read(path);
            if (record.getProperty("identity").equals(MailIdentity.of(message))) {
                return !record.getProperty("key").equals(key);
            }
        }
        return false;
    }

    @Override
    public List<Message> getLocalNetmail(FTNAddr address) {
        List<Message> result = new ArrayList<>();
        for (Path path : FileRecords.list(root.resolve("netmail"))) {
            Properties record = FileRecords.read(path);
            if ("LOCAL".equals(record.getProperty("state"))
                    && address.toString().equals(record.getProperty("toAddress"))) {
                result.add(MailRecord.get(record));
            }
        }
        return result;
    }

    @Override
    public List<QueuedNetmail> getQueuedNetmail() {
        List<QueuedNetmail> result = new ArrayList<>();
        for (Path path : FileRecords.list(root.resolve("netmail"))) {
            Properties record = FileRecords.read(path);
            if ("QUEUED".equals(record.getProperty("state"))) {
                result.add(new QueuedNetmail(record.getProperty("key"),
                                             record.getProperty("nextHop"),
                                             MailRecord.get(record)));
            }
        }
        return result;
    }

    @Override
    public void visitQueuedNetmail(NetmailStore.QueuedHandler visitor) throws IOException {
        for (Path path : FileRecords.list(root.resolve("netmail"))) {
            Properties record = FileRecords.read(path);
            if ("QUEUED".equals(record.getProperty("state"))) {
                visitor.accept(new QueuedNetmail(record.getProperty("key"),
                                                 record.getProperty("nextHop"),
                                                 MailRecord.get(record)));
            }
        }
    }

    @Override
    public void beforeExport(Tosser tosser) throws IOException {
    }

    @Override
    public void afterExport() {
        for (Path path : FileRecords.list(root.resolve("echo"))) {
            Properties record = FileRecords.read(path);
            String consumers = record.getProperty("consumers", "");
            boolean complete = consumers.isEmpty()
                    || List.of(consumers.split(","))
                           .stream()
                           .allMatch(item -> record.containsKey("done." + item));
            if (complete && record.containsKey("body")) {
                record.remove("body");
                FileRecords.write(path, record);
            }
        }
    }

    @Override
    public synchronized void markNetmailSent(String key) {
        Path path = netmailRecord(key);
        Properties record = FileRecords.read(path);
        if (record.isEmpty()) {
            throw new IllegalArgumentException("Unknown delivery");
        }
        record.setProperty("state", "SENT");
        record.remove("body");
        FileRecords.write(path, record);
    }

    @Override
    public synchronized void routeUnpackedNetmail(String key, String nextHop) {
        Path path = netmailRecord(key);
        Properties record = FileRecords.read(path);
        if (!NetmailStore.unpacked(record.getProperty("nextHop"))) {
            throw new IllegalStateException("Delivery already has a fixed next hop");
        }
        record.setProperty("nextHop", nextHop);
        FileRecords.write(path, record);
    }

    @Override
    public List<String> getAreas() {
        return catalog.stringPropertyNames()
                      .stream()
                      .filter(key -> key.startsWith("area."))
                      .map(key -> key.substring(5))
                      .sorted()
                      .collect(Collectors.toList());
    }

    @Override
    public boolean isAreaExists(String name) {
        return catalog.containsKey("area." + areaName(name));
    }

    @Override
    public boolean canReadEcho(Link link, String area) {
        String access = catalog.getProperty(grant(link, area), "");
        return access.equals("READ") || access.equals("BOTH");
    }

    @Override
    public boolean canWriteEcho(Link link, String area) {
        String access = catalog.getProperty(grant(link, area), "");
        return access.equals("WRITE") || access.equals("BOTH");
    }

    @Override
    public List<String> getSubscriptions(Link link) {
        return getAreas().stream()
                         .filter(area -> canReadEcho(link, area))
                         .collect(Collectors.toList());
    }

    @Override
    public void addSubscription(Link link, String area) {
        configureEchoSubscription(link, area, AccessLevel.READ, EchoHistory.NEW_ONLY);
    }

    @Override
    public Iterator<Message> getMessages(String area) {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Message> getMessages(String area, long start, long count) {
        return getMessages(area);
    }

    @Override
    public Iterator<Message> getMessages(Link link) {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Message> getMessages(Link link, String area) {
        return getMessages(link);
    }

    @Override
    public Iterator<Message> getMessages(Link link, String area, long start, long count) {
        return getMessages(link);
    }

    @Override
    public Message getMessage(String area, String id) {
        Iterator<Message> messages = getMessages(area);
        while (messages.hasNext()) {
            Message message = messages.next();
            if (id.equals(message.getMsgId())) {
                return message;
            }
        }
        return null;
    }

    @Override
    public List<Message> getMessage(String id) {
        List<Message> result = new ArrayList<>();
        for (String area : getAreas()) {
            Message message = getMessage(area, id);
            if (message != null) {
                result.add(message);
            }
        }
        return result;
    }

    @Override
    public long getMessageSize(String area) {
        return 0;
    }

    @Override
    public void setLinkLastMessage(Link link, String area, Long id) {
        throw new UnsupportedOperationException("File queues use per-message delivery receipts");
    }

    @Override
    public void visitEchoRange(String area, long first, long last, MessageIteration.Handler handler)
            throws IOException {
        throw new UnsupportedOperationException("Transit has no reader history; use the legacy profile");
    }

    @Override
    public void close() {
        ownership.close();
    }
}
