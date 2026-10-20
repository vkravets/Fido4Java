package org.fidonet.fileecho;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.robots.RobotAccessRule;
import org.fidonet.fileecho.storage.FileArea;
import org.fidonet.jftn.script.MailAutomationRuntime;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Authorized exact-name resend; never reads arbitrary paths or changes subscriptions. */
final class FileEchoResender {
    private final MailAutomationRuntime api;
    private final FileEchoAreaService store;
    private final Charset charset;

    FileEchoResender(MailAutomationRuntime api, FileEchoAreaService store, Charset charset) {
        this.api = api;
        this.store = store;
        this.charset = charset;
    }

    void resend(FTNAddr address, String area, String filename, String requestId)
            throws IOException {
        String tag = FileEchoAreaService.tag(area);
        Link peer = ((JFtnConfig) api.getConfig()).getLink(address);
        FileArea record = store.area(tag);
        RobotAccessRule robot = api.getRobotAccess().find("FileFix", address);
        RobotAccessRule tic = api.getRobotAccess().find("Tic", address);
        if (peer == null || record == null || !record.enabled || robot == null || tic == null
                || !robot.allows(tag) || !tic.allows(tag)
                || !store.subscriptions(address).contains(tag)) {
            throw new IllegalArgumentException("Resend requires an allowed linked file area and TIC access");
        }
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,80}") || filename == null
                || !filename.matches("[A-Za-z0-9_!#$%&'()@^`{}~-]{1,8}(\\.[A-Za-z0-9_!#$%&'()@^`{}~-]{1,3})?")) {
            throw new IllegalArgumentException("Resend requires an exact DOS 8.3 filename and request ID");
        }
        Path source = payload(Path.of(record.directory), filename);
        String name = source.getFileName().toString();
        FileChecksum checksum = FileChecksum.read(source);
        FTNAddr local = peer.getMyaddr().get(0);
        List<String> lines = new ArrayList<>(List.of("Area " + tag,
                                                     "File " + name,
                                                     "Size " + checksum.size,
                                                     "Crc " + checksum.crc,
                                                     "Date "
                                                     + Files.getLastModifiedTime(source)
                                                            .toInstant()
                                                            .getEpochSecond(),
                                                     "Origin " + TicRouting.address(local),
                                                     "From " + TicRouting.address(local),
                                                     "To " + TicRouting.address(address),
                                                     "Pw " + tic.getDeliveryPassword(),
                                                     "Desc Requested file resend",
                                                     "Created by JFtn FileFix",
                                                     TicRouting.path(local, Instant.now())));
        lines.addAll(TicRouting.seen(Set.of(local, address)));
        String text = String.join("\r\n", lines) + "\r\n";
        api.getFileQueue()
           .enqueue(peer,
                    "filefix-resend:" + requestId + ":" + tag + ":" + name.toLowerCase(Locale.ROOT),
                    source,
                    name,
                    text.getBytes(charset));
    }

    private static Path payload(Path directory, String name) throws IOException {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("File area directory is unavailable");
        }
        List<Path> matches;
        try (Stream<Path> files = Files.list(directory)) {
            matches = files.filter(path -> path.getFileName().toString().equalsIgnoreCase(name))
                           .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                           .collect(Collectors.toList());
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("File is missing or its case-insensitive name is ambiguous");
        }
        return matches.get(0);
    }
}
