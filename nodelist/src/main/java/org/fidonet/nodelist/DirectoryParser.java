package org.fidonet.nodelist;

import org.fidonet.types.FTNAddr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Streaming FTS-5000/5002 parser. A parser instance owns exactly one import's hierarchy. */
public final class DirectoryParser {
    @FunctionalInterface
    public interface Sink {
        void accept(DirectoryEntry entry) throws IOException;
    }

    public static final class Result {
        private final long records;
        private final String header;
        private final Integer checksum;

        private Result(long records, String header, Integer checksum) {
            this.records = records;
            this.header = header;
            this.checksum = checksum;
        }

        public long getRecords() {
            return records;
        }
        public String getHeader() {
            return header;
        }
        public Integer getChecksum() {
            return checksum;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryParser.class);
    private static final byte[] NEW_POINT_KEEPER = ";S Created by New Point Keeper v.0.0.0.7.".getBytes(StandardCharsets.US_ASCII);
    private int arc;
    private boolean newPointKeeper;
    private static final Pattern CHECKSUM = Pattern.compile(":\\s*(\\d+)\\s*$");
    private static final Pattern ADDRESS = Pattern.compile("(?:(\\d+):)?(\\d+)/(\\d+)(?:\\.(\\d+))?");
    private static final Pattern USER_ADDRESS = Pattern.compile("(?:\\d+:)?\\d+/\\d+(?:\\.\\d+)?\\s*$");
    private final DirectorySource source;
    private final Sink sink;
    private final CharsetDecoder decoder;
    private String header = "";
    private Integer expected;
    private int zone;
    private int net;
    private Integer region;
    private Integer hub;
    private FTNAddr boss;
    private long line;
    private long records;
    private int fidouserWidth = -1;
    private String lastSurname = "";

    private DirectoryParser(DirectorySource source, Sink sink) {
        this.source = source;
        this.sink = sink;
        decoder = source.getCharset()
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        zone = source.getDefaultZone();
        net = source.getDefaultNet();
    }

    /** Does not close input. XMODEM uses canonical CRLF; opt-in ARC uses original body bytes. */
    public static Result parse(InputStream input, DirectorySource source, Sink sink)
            throws IOException {
        return new DirectoryParser(source,
                                   sink).read(new PushbackInputStream(new BufferedInputStream(input), 1));
    }

    private Result read(PushbackInputStream input) throws IOException {
        int crc = 0;
        byte[] bytes;
        while ((bytes = readLine(input)) != null) {
            line++;
            if (line > 1) {
                for (byte value : bytes) {
                    crc = crc16(crc, value & 255);
                }
                crc = crc16(crc16(crc, 13), 10);
            }
            if (line == 2) {
                newPointKeeper = Arrays.equals(bytes, NEW_POINT_KEEPER);
            }
            readRecord(bytes);
        }
        if (line == 0 || records == 0) {
            throw failure("No directory records; refusing an empty replacement", null);
        }
        if (!source.isChecksumIgnored()
                && (source.isChecksumRequired() || source.isNewPointKeeperChecksum())
                && expected == null) {
            throw failure("Missing header CRC", null);
        }
        if (!source.isChecksumIgnored() && expected != null && expected != crc) {
            if (!source.isNewPointKeeperChecksum() || !newPointKeeper || expected != arc) {
                throw failure("CRC mismatch: expected " + expected + ", computed " + crc, null);
            }
            LOG.info("Validated directory header CRC using new-point-keeper compatibility: source={}, CRC-16/ARC={}",
                     source.getId(),
                     arc);
        }
        return new Result(records, header, expected);
    }

    private void readRecord(byte[] bytes) throws IOException {
        try {
            String text = decoder.decode(ByteBuffer.wrap(bytes)).toString();
            if (line == 1 && text.startsWith(";")) {
                readHeader(text);
            }
            consume(text, bytes.length);
        } catch (IllegalArgumentException | IOException e) {
            throw failure(e.getMessage(), e);
        }
    }

    private void readHeader(String text) {
        header = text;
        Matcher match = CHECKSUM.matcher(text);
        if (!match.find()) {
            return;
        }
        expected = Integer.valueOf(match.group(1));
        if (expected > 65535) {
            throw new IllegalArgumentException("CRC must be an unsigned 16-bit number");
        }
    }

    private byte[] readLine(PushbackInputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == 26) {
                if (input.read() != -1) {
                    throw failure("Data after DOS EOF marker", null);
                }
                break;
            }
            if (line > 0 && source.isNewPointKeeperChecksum()) {
                arc = crc16Arc(arc, value);
            }
            if (value == 13 || value == 10) {
                if (value == 13) {
                    int next = input.read();
                    if (next == 10 && line > 0 && source.isNewPointKeeperChecksum()) {
                        arc = crc16Arc(arc, next);
                    }
                    if (next != 10 && next != -1) {
                        input.unread(next);
                    }
                }
                return bytes.toByteArray();
            }
            if (value < 32 || value == 127) {
                if (value != 9 || source.getFormat() != DirectorySource.Format.FIDOUSER_TEXT) {
                    throw failure("Unexpected control character", null);
                }
            }
            if (bytes.size() >= 65536) {
                throw failure("Directory line exceeds 65536 bytes", null);
            }
            bytes.write(value);
        }
        return bytes.size() == 0 ? null : bytes.toByteArray();
    }

    static int crc16Arc(int crc, int value) {
        crc ^= value;
        for (int bit = 0; bit < 8; bit++) {
            crc = (crc >>> 1) ^ ((crc & 1) != 0 ? 0xa001 : 0);
        }
        return crc & 0xffff;
    }

    static int crc16(int crc, int value) {
        crc ^= value << 8;
        for (int bit = 0; bit < 8; bit++) {
            crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
        }
        return crc & 0xffff;
    }

    private void consume(String text, int width) throws IOException {
        if (text.isEmpty() || text.startsWith(";")) {
            return;
        }
        if (source.getFormat() == DirectorySource.Format.FIDOUSER
                || source.getFormat() == DirectorySource.Format.FIDOUSER_TEXT) {
            fidouser(text, width);
            return;
        }
        csv(text);
    }

    private void csv(String text) throws IOException {
        String[] fields = text.split(",", -1);
        String keyword = fields[0].trim().toUpperCase(Locale.ROOT);
        if (keyword.equals("BOSS")) {
            if (source.getFormat() != DirectorySource.Format.BOSS
                    && source.getFormat() != DirectorySource.Format.POSS) {
                throw new IllegalArgumentException("Boss line requires BOSS or POSS format");
            }
            if (fields.length < 2) {
                throw new IllegalArgumentException("Missing boss address");
            }
            boss = address(fields[1].trim(), true);
            if (boss.getPnt() != 0) {
                throw new IllegalArgumentException("Boss must be a node address");
            }
            return;
        }
        if (fields.length < 7) {
            throw new IllegalArgumentException("Expected at least seven CSV fields");
        }
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].trim();
        }
        int number = positive(fields[1]);
        FTNAddr address = entryAddress(keyword, number, fields);
        if (address == null) {
            return;
        }
        List<String> flags = Arrays.asList(Arrays.copyOfRange(fields, 7, fields.length));
        sink.accept(new DirectoryEntry(source.getId(),
                                       address,
                                       keyword,
                                       fields[2],
                                       display(fields[3]),
                                       display(fields[4]),
                                       fields[5],
                                       fields[6],
                                       region,
                                       hub,
                                       flags,
                                       line));
        records++;
    }

    private FTNAddr entryAddress(String keyword, int number, String[] fields) {
        switch (source.getFormat()) {
            case BOSS :
            case POSS :
                if (!keyword.isEmpty() && !keyword.equals("POINT") && !keyword.equals("PVT")
                        && !keyword.equals("HOLD") && !keyword.equals("DOWN")) {
                    throw new IllegalArgumentException("Unexpected point entry keyword: "
                                                       + keyword);
                }
                return point(number);
            case FAKENET :
                if (keyword.equals("REGION")) {
                    boss = null;
                    return null;
                }
                if (keyword.equals("HOST")) {
                    boss = fakeBoss(fields);
                    if (boss.getPnt() != 0) {
                        throw new IllegalArgumentException("Fake-net host must identify a boss node");
                    }
                    return null;
                }
                if (keyword.equals("ZONE")) {
                    zone = number;
                    boss = null;
                    return null;
                }
                return point(number);
            default :
                return node(keyword, number);
        }
    }

    private FTNAddr node(String keyword, int number) {
        if (keyword.equals("POINT")) {
            return point(number);
        }
        if (keyword.equals("ZONE")) {
            zone = number;
            net = number;
            region = number;
            hub = null;
            boss = new FTNAddr(zone, net, 0, 0);
            return boss;
        }
        if (zone == 0) {
            throw new IllegalArgumentException("Missing Zone context; configure a default zone for a segment");
        }
        if (keyword.equals("REGION") || keyword.equals("HOST")) {
            net = number;
            if (keyword.equals("REGION")) {
                region = number;
            }
            hub = null;
            boss = new FTNAddr(zone, net, 0, 0);
            return boss;
        }
        if (net == 0) {
            throw new IllegalArgumentException("Missing Host/Region context; configure a default net for a segment");
        }
        if (keyword.equals("HUB")) {
            hub = number;
        }
        boss = new FTNAddr(zone, net, number, 0);
        return boss;
    }

    private FTNAddr fakeBoss(String[] fields) {
        String value = null;
        List<String> flags = Arrays.asList(Arrays.copyOfRange(fields, 7, fields.length));
        for (String flag : DirectoryFlags.normalized(flags)) {
            if (!flag.toUpperCase(Locale.ROOT).startsWith("BOSS:")) {
                continue;
            }
            String next = flag.substring(5);
            if (value != null && !address(value, false).equals(address(next, false))) {
                throw new IllegalArgumentException("Conflicting UBOSS mappings");
            }
            value = next;
        }
        return address(value == null ? fields[2] : value, false);
    }

    private FTNAddr point(int number) {
        if (boss == null) {
            throw new IllegalArgumentException("Point entry has no preceding boss node");
        }
        return new FTNAddr(boss.getZone(), boss.getNet(), boss.getNode(), number);
    }

    private void fidouser(String text, int width) throws IOException {
        Matcher match = USER_ADDRESS.matcher(text);
        if (!match.find()) {
            throw new IllegalArgumentException("Missing FidoUser address");
        }
        int comma = text.indexOf(',');
        if (comma < 1 || comma >= match.start()) {
            throw new IllegalArgumentException("Expected surname, given name before FidoUser address");
        }
        String surname = text.substring(0, comma);
        if (source.getFormat() == DirectorySource.Format.FIDOUSER) {
            if (match.start() < 45 || (fidouserWidth >= 0 && width != fidouserWidth)
                    || surname.compareToIgnoreCase(lastSurname) < 0) {
                throw new IllegalArgumentException("FidoUser requires fixed-width, sorted records and address at column 46 or later");
            }
            fidouserWidth = width;
            lastSurname = surname;
        } else if (match.start() < 2
                || !text.substring(match.start() - 2, match.start()).matches("\\s{2}|.*\\t")) {
            throw new IllegalArgumentException("FidoUser text address must follow two spaces or a tab");
        }
        FTNAddr address = address(match.group().trim(), false);
        String given = text.substring(comma + 1, match.start()).trim();
        sink.accept(new DirectoryEntry(source.getId(),
                                       address,
                                       address.getPnt() > 0 ? "POINT" : "",
                                       "",
                                       "",
                                       display(given + " " + surname),
                                       "-Unpublished-",
                                       "",
                                       null,
                                       null,
                                       List.of(),
                                       line));
        records++;
    }

    private FTNAddr address(String text, boolean requireZone) {
        Matcher match = ADDRESS.matcher(text);
        if (!match.matches() || requireZone && match.group(1) == null) {
            throw new IllegalArgumentException("Invalid FTN address: " + text);
        }
        int z = match.group(1) == null ? zone : positive(match.group(1));
        if (z == 0) {
            throw new IllegalArgumentException("Missing default zone for " + text);
        }
        return new FTNAddr(z,
                           positive(match.group(2)),
                           unsigned(match.group(3)),
                           match.group(4) == null ? 0 : unsigned(match.group(4)));
    }

    private static int positive(String text) {
        int value = unsigned(text);
        if (value == 0) {
            throw new IllegalArgumentException("Zero is not a valid directory number");
        }
        return value;
    }

    private static int unsigned(String text) {
        if (!text.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid directory number: " + text);
        }
        int value = Integer.parseInt(text);
        if (value > 65535) {
            throw new IllegalArgumentException("Directory number exceeds 65535: " + text);
        }
        return value;
    }

    private static String display(String value) {
        return value.replace('_', ' ');
    }

    private IOException failure(String message, Throwable cause) {
        return new IOException(source.getId() + ":" + Math.max(1, line) + ": " + message, cause);
    }
}
