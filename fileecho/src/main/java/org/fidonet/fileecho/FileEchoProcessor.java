package org.fidonet.fileecho;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.jftn.script.FileDeliveryQueue;
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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Validates and stores incoming deliveries before publishing callbacks and durable receipts. */
final class FileEchoProcessor implements FileEchoService {
    private static final Logger LOG = LoggerFactory.getLogger(FileEchoProcessor.class);
    private final MailScripts api;
    private final JFtnConfig config;
    private final Map<String, Path> areas = new TreeMap<>();
    private final Charset charset;

    FileEchoProcessor(MailScripts api) {
        this.api = api;
        config = (JFtnConfig) api.getConfig();
        charset = Charset.forName(config.getValue("TicCharset", "IBM437"));
        List<String> declarations = config.getValuesAsList("FileArea");
        for (String value : declarations == null ? List.<String>of() : declarations) {
            String[] fields = value.split("\\|", -1);
            if (fields.length < 2 || fields.length > 3 || !fields[0].matches("[A-Za-z0-9_.-]{1,60}")
                    || fields[1].isBlank()) {
                throw new IllegalArgumentException("FileArea=tag|directory[|description]");
            }
            String tag = fields[0].toLowerCase(Locale.ROOT);
            if (areas.put(tag, Path.of(fields[1]).toAbsolutePath().normalize()) != null) {
                throw new IllegalArgumentException("Duplicate FileArea " + tag);
            }
        }
    }

    @Override
    public Set<String> areas() {
        return Set.copyOf(areas.keySet());
    }

    @Override
    public Set<String> subscriptions(FTNAddr peer) throws IOException {
        String saved = api.getState().get("filefix-subscriptions:" + peer);
        Set<String> result = new TreeSet<>();
        if (saved != null && !saved.isEmpty()) {
            result.addAll(Arrays.asList(saved.split("\n")));
        }
        return result;
    }

    @Override
    public void subscribe(FTNAddr peer, String area, boolean enabled) throws IOException {
        if (!areas.containsKey(area) || config.getLink(peer) == null) {
            throw new IllegalArgumentException("Unknown file area or link");
        }
        Set<String> linked = subscriptions(peer);
        if (enabled) {
            linked.add(area);
        } else {
            linked.remove(area);
        }
        api.getState().put("filefix-subscriptions:" + peer, String.join("\n", linked));
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
        }
    }

    private void process(ReceivedFile ticket, List<ReceivedFile> inbox) throws IOException {
        TicDocument tic = new TicDocument(ticket.getPath(), charset);
        String tag = tic.value("area").toLowerCase(Locale.ROOT);
        authorize(tic, ticket, tag);
        FTNAddr origin = TicDocument.address(tic.value("origin"));
        Set<FTNAddr> seen = tic.addresses("seenby");
        Set<FTNAddr> paths = tic.addresses("path");
        if (config.getLocalAddresses().stream().anyMatch(paths::contains)) {
            throw new IOException("TIC path loop");
        }
        String name = ReceivedFile.safeName(tic.value("file"));
        if (!name.matches("[A-Za-z0-9_~-]{1,8}(\\.[A-Za-z0-9_~-]{1,3})?")
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
        Path stored = store(payload.getPath(), areas.get(tag), receipt + "-" + name, checksum);
        forward(tic, tag, name, stored, ticket.getPeer(), seen, receipt);
        api.publishFile(tag, stored, name, ticket.getPeer(), receipt);
        api.getState().put("tic:" + receipt, "done");
        LOG.info("Accepted fileecho {} / {} from {} ({} bytes)",
                 tag,
                 name,
                 ticket.getPeer(),
                 checksum.size);
    }

    private void authorize(TicDocument tic, ReceivedFile ticket, String tag) throws IOException {
        RobotAccessRule rule = api.getRobotAccess().find("Tic", ticket.getPeer());
        if (rule == null || !TicDocument.address(tic.value("from")).equals(ticket.getPeer())
                || !areas.containsKey(tag) || !rule.allows(tag) || tic.value("pw").isEmpty()
                || !rule.verifyPassword(tic.value("pw"))) {
            throw new IOException("TIC sender, area or password is not authorized");
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
        for (ReceivedFile candidate : inbox) {
            if (!candidate.getPeer().equals(ticket.getPeer())
                    || !candidate.getName().equalsIgnoreCase(name)) {
                continue;
            }
            FileChecksum checksum = FileChecksum.read(candidate.getPath());
            if (checksum.crc.equalsIgnoreCase(tic.value("crc")) && (tic.value("size").isEmpty()
                    || tic.value("size").equals(Long.toString(checksum.size)))) {
                return candidate;
            }
        }
        return null;
    }

    private Path store(Path payload, Path directory, String name, FileChecksum checksum)
            throws IOException {
        Files.createDirectories(directory);
        Path stored = directory.resolve(name);
        Path staging = Files.createTempFile(directory, ".incoming-", ".tmp");
        try {
            FileDeliveryQueue.copy(payload, staging);
            if (!checksum.same(FileChecksum.read(staging))) {
                throw new IOException("Payload changed while storing it");
            }
            Files.move(staging,
                       stored,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
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
