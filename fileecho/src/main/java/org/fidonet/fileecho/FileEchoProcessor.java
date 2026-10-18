package org.fidonet.fileecho;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.jftn.script.FileDeliveryQueue;
import org.fidonet.jftn.script.FileEchoEvent;
import org.fidonet.jftn.script.FileEchoService;
import org.fidonet.jftn.script.MailScripts;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.jftn.script.ScriptState;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Validates and stores incoming deliveries before publishing callbacks and durable receipts. */
final class FileEchoProcessor implements FileEchoService {
    private static final Logger LOG = LoggerFactory.getLogger(FileEchoProcessor.class);
    private final MailScripts api;
    private final JFtnConfig config;
    private final FileEchoStore store;
    private final Charset charset;

    FileEchoProcessor(MailScripts api) throws IOException {
        this.api = api;
        config = (JFtnConfig) api.getConfig();
        charset = Charset.forName(config.getValue("TicCharset", "IBM437"));
        store = new FileEchoStore(config, api.getDatabase(), api.getState());
    }

    FileEchoStore store() {
        return store;
    }

    @Override
    public Set<String> areas() throws IOException {
        Set<String> result = new TreeSet<>();
        for (FileAreaRecord area : store.list()) {
            if (area.enabled) {
                result.add(area.name);
            }
        }
        return result;
    }

    @Override
    public Set<String> subscriptions(FTNAddr peer) throws IOException {
        return store.subscriptions(peer);
    }

    @Override
    public void subscribe(FTNAddr peer, String area, boolean enabled) throws IOException {
        if (!areas().contains(area) || config.getLink(peer) == null) {
            throw new IllegalArgumentException("Unknown file area or link");
        }
        store.subscribe(peer, area, enabled);
    }

    void scan() throws IOException {
        List<ReceivedFile> inbox = api.getReceivedFiles();
        for (ReceivedFile ticket : inbox) {
            if (ticket.getName().toLowerCase(Locale.ROOT).endsWith(".tic")) {
                processSafely(ticket, inbox);
            }
        }
    }

    private void processSafely(ReceivedFile ticket, List<ReceivedFile> inbox) {
        try {
            process(ticket, inbox);
        } catch (IOException | IllegalArgumentException failure) {
            LOG.warn("Deferred TIC {} from {}: {}",
                     ticket.getPath().getFileName(),
                     ticket.getPeer(),
                     failure.getMessage());
            String reason = failure.getMessage() == null
                    ? "Fileecho processing failed"
                    : failure.getMessage();
            try {
                api.publishFileEcho(new FileEchoEvent("error",
                                                      "",
                                                      null,
                                                      ticket.getName(),
                                                      ticket.getPeer(),
                                                      ScriptState.digest(ticket.getPath()
                                                                         + ":"
                                                                         + reason),
                                                      List.of(),
                                                      reason));
            } catch (IOException | RuntimeException notification) {
                LOG.warn("Cannot publish fileecho diagnostic", notification);
            }
        }
    }

    private void process(ReceivedFile ticket, List<ReceivedFile> inbox) throws IOException {
        TicDocument tic = new TicDocument(ticket.getPath(), charset);
        String tag = FileEchoStore.tag(tic.value("area"));
        authorize(tic, ticket, tag);
        FTNAddr origin = TicDocument.address(tic.value("origin"));
        Set<FTNAddr> seen = tic.addresses("seenby");
        Set<FTNAddr> paths = tic.addresses("path");
        if (config.getLocalAddresses().stream().anyMatch(paths::contains)) {
            throw new IOException("TIC path loop");
        }
        String name = ReceivedFile.safeName(tic.value("file"));
        if (!name.matches("[A-Za-z0-9!#$%&'()@^_`{}~-]{1,8}(\\.[A-Za-z0-9!#$%&'()@^_`{}~-]{1,3})?")
                || name.toLowerCase(Locale.ROOT).endsWith(".tic")) {
            throw new IOException("TIC File must be a DOS 8.3 payload name");
        }
        if (!tic.value("crc").matches("[0-9a-fA-F]{8}")) {
            throw new IOException("Invalid TIC CRC");
        }
        ReceivedFile payload = findPayload(ticket, inbox, name, tic);
        if (payload == null) {
            return;
        }
        FileChecksum checksum = FileChecksum.read(payload.getPath());
        String receipt = ScriptState.digest(tag
                                            + ":"
                                            + origin
                                            + ":"
                                            + name.toLowerCase(Locale.ROOT)
                                            + ":"
                                            + checksum.sha);
        if (api.getState().get("tic:" + receipt) != null) {
            return;
        }
        if (!checksum.crc.equalsIgnoreCase(tic.value("crc")) || (!tic.value("size").isEmpty()
                && !tic.value("size").equals(Long.toString(checksum.size)))) {
            throw new IOException("Payload changed after matching TIC");
        }
        FileAreaRecord area = store.createAfterValidation(tag, ticket.getPeer());
        String placementKey = "tic-placement:" + receipt;
        String placement = api.getState().get(placementKey);
        Path directory = Path.of(area.directory);
        if (placement == null) {
            placement = occupied(directory, name) ? "duplicate" : "accepted";
            api.getState().put(placementKey, placement);
        }
        boolean duplicate = placement.equals("duplicate");
        Path destination = duplicate ? directory.resolve("dupes").resolve(receipt) : directory;
        Path stored = store(payload.getPath(), destination, name, checksum);
        List<String> descriptions = new ArrayList<>(tic.values("desc"));
        descriptions.addAll(tic.values("ldesc"));
        api.publishFileEcho(new FileEchoEvent(placement,
                                              tag,
                                              stored,
                                              name,
                                              ticket.getPeer(),
                                              receipt,
                                              descriptions,
                                              duplicate
                                                      ? "Filename already exists; original retained"
                                                      : ""));
        if (!duplicate) {
            forward(tic, tag, name, stored, ticket.getPeer(), seen, receipt);
            api.publishFile(tag, stored, name, ticket.getPeer(), receipt);
        }
        api.getState().put("tic:" + receipt, "done");
        LOG.info("Processed fileecho {} / {} from {} ({} bytes, {})",
                 tag,
                 name,
                 ticket.getPeer(),
                 checksum.size,
                 placement);
    }

    private boolean occupied(Path directory, String name) throws IOException {
        if (Set.of("files.bbs", "file.bbs", "dupes").contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.anyMatch(path -> path.getFileName().toString().equalsIgnoreCase(name));
        }
    }

    private void authorize(TicDocument tic, ReceivedFile ticket, String tag) throws IOException {
        RobotAccessRule rule = api.getRobotAccess().find("Tic", ticket.getPeer());
        if (rule == null) {
            throw new IOException("No enabled TIC rule for the transport peer");
        }
        if (!TicDocument.address(tic.value("from")).equals(ticket.getPeer())) {
            throw new IOException("TIC From differs from the transport peer");
        }
        if (!store.canReceive(tag, ticket.getPeer())) {
            throw new IOException("File area import or automatic creation is not permitted: "
                                  + tag);
        }
        if (!rule.allows(tag)) {
            throw new IOException("TIC area is excluded by the peer's area masks: " + tag);
        }
        if (tic.value("pw").isEmpty() || !rule.verifyPassword(tic.value("pw"))) {
            throw new IOException("TIC password is missing or does not match");
        }
        if (!tic.value("to").isEmpty()
                && !config.getLocalAddresses().contains(TicDocument.address(tic.value("to")))) {
            throw new IOException("TIC is addressed to another station");
        }
    }

    private ReceivedFile findPayload(ReceivedFile ticket,
                                     List<ReceivedFile> inbox,
                                     String name,
                                     TicDocument tic)
            throws IOException {
        boolean found = false;
        for (ReceivedFile candidate : inbox) {
            if (!candidate.getPeer().equals(ticket.getPeer())
                    || !candidate.getName().equalsIgnoreCase(name)) {
                continue;
            }
            FileChecksum checksum = FileChecksum.read(candidate.getPath());
            found = true;
            if (checksum.crc.equalsIgnoreCase(tic.value("crc")) && (tic.value("size").isEmpty()
                    || tic.value("size").equals(Long.toString(checksum.size)))) {
                return candidate;
            }
        }
        if (found) {
            throw new IOException("TIC CRC or size does not match the received payload");
        }
        return null;
    }

    private Path store(Path payload, Path directory, String name, FileChecksum checksum)
            throws IOException {
        Files.createDirectories(directory);
        Path stored = directory.resolve(name);
        if (Files.exists(stored, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(stored, LinkOption.NOFOLLOW_LINKS)
                    || !checksum.same(FileChecksum.read(stored))) {
                throw new IOException("Previously stored file changed; refusing to overwrite it");
            }
            return stored;
        }
        Path staging = Files.createTempFile(directory, ".incoming-", ".tmp");
        try {
            FileDeliveryQueue.copy(payload, staging);
            if (!checksum.same(FileChecksum.read(staging))) {
                throw new IOException("Payload changed while storing it");
            }
            Files.move(staging, stored);
        } finally {
            Files.deleteIfExists(staging);
        }
        return stored;
    }

    private void forward(TicDocument tic,
                         String tag,
                         String name,
                         Path stored,
                         FTNAddr sender,
                         Set<FTNAddr> seen,
                         String receipt)
            throws IOException {
        List<Link> recipients = new ArrayList<>();
        for (Link link : config.getConfiguredLinks()) {
            RobotAccessRule rule = api.getRobotAccess().find("Tic", link.getAddr());
            if (!link.getAddr().equals(sender) && !seen.contains(link.getAddr())
                    && !config.getLocalAddresses().contains(link.getAddr()) && rule != null
                    && rule.allows(tag) && subscriptions(link.getAddr()).contains(tag)) {
                recipients.add(link);
            }
        }
        Set<FTNAddr> seenAll = new LinkedHashSet<>(seen);
        seenAll.add(sender);
        seenAll.addAll(config.getLocalAddresses());
        recipients.forEach(link -> seenAll.add(link.getAddr()));
        for (Link link : recipients) {
            send(tic, link, stored, name, seenAll, receipt);
        }
    }

    private void send(TicDocument tic,
                      Link link,
                      Path stored,
                      String name,
                      Set<FTNAddr> seen,
                      String receipt)
            throws IOException {
        List<String> output = new ArrayList<>();
        Set<String> replaced = Set.of("from",
                                      "to",
                                      "pw",
                                      "created",
                                      "seenby",
                                      "path",
                                      "magic",
                                      "receiptrequest");
        for (String line : tic.lines) {
            if (!replaced.contains(line.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT))) {
                output.add(line);
            }
        }
        FTNAddr local = link.getMyaddr().get(0);
        output.add("From " + local);
        output.add("To " + link.getAddr());
        output.add("Pw " + api.getRobotAccess().find("Tic", link.getAddr()).getDeliveryPassword());
        output.add("Created by JFtn fileecho");
        tic.values("path").forEach(value -> output.add("Path " + value));
        output.add("Path " + local + " " + System.currentTimeMillis() / 1000);
        seen.forEach(value -> output.add("Seenby " + value));
        api.getFileQueue()
           .enqueue(link,
                    receipt,
                    stored,
                    name,
                    (String.join("\r\n", output) + "\r\n").getBytes(charset));
    }
}
