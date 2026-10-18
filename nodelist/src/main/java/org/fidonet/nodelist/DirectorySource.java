package org.fidonet.nodelist;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Explicit format and addressing context: never guesses a fake net from a filename. */
public final class DirectorySource {
    public enum Format {
        NODELIST, BOSS, POINT, POSS, FAKENET, FIDOUSER, FIDOUSER_TEXT
    }

    private final String id;
    private final Format format;
    private final Path path;
    private final int zone;
    private final int net;
    private final Charset charset;
    private final boolean requireChecksum;
    private final boolean ignoreChecksum;

    public DirectorySource(String id,
                           Format format,
                           Path path,
                           int zone,
                           int net,
                           Charset charset,
                           boolean requireChecksum) {
        this(id, format, path, zone, net, charset, requireChecksum, false);
    }

    private DirectorySource(String id,
                            Format format,
                            Path path,
                            int zone,
                            int net,
                            Charset charset,
                            boolean requireChecksum,
                            boolean ignoreChecksum) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("Directory source ID must contain 1..80 letters, digits, _ or -");
        }
        if (zone < 0 || zone > 65535 || net < 0 || net > 65535) {
            throw new IllegalArgumentException("Default zone and net must be unsigned 16-bit numbers");
        }
        this.id = id;
        this.format = Objects.requireNonNull(format);
        this.path = Objects.requireNonNull(path).toAbsolutePath().normalize();
        this.zone = zone;
        this.net = net;
        this.charset = Objects.requireNonNull(charset);
        this.requireChecksum = requireChecksum;
        this.ignoreChecksum = ignoreChecksum;
    }

    /** id|format|file[|zone|net|charset|requireChecksum]. */
    public static DirectorySource parse(String value) {
        String[] fields = value.split("\\|", -1);
        if (fields.length < 3 || fields.length > 7 || fields[2].trim().isEmpty()) {
            throw new IllegalArgumentException("Expected DirectorySource=id|format|file[|zone|net|charset|requireChecksum]");
        }
        String required = field(fields, 6, "false");
        if (!required.equalsIgnoreCase("true") && !required.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("requireChecksum must be true or false");
        }
        return new DirectorySource(fields[0].trim(),
                                   Format.valueOf(fields[1].trim().toUpperCase(Locale.ROOT)),
                                   Path.of(fields[2].trim()),
                                   Integer.parseInt(field(fields, 3, "0")),
                                   Integer.parseInt(field(fields, 4, "0")),
                                   Charset.forName(field(fields,
                                                         5,
                                                         StandardCharsets.US_ASCII.name())),
                                   Boolean.parseBoolean(required));
    }

    private static String field(String[] fields, int index, String fallback) {
        return index < fields.length && !fields[index].trim().isEmpty()
                ? fields[index].trim()
                : fallback;
    }

    public String getId() {
        return id;
    }

    public Format getFormat() {
        return format;
    }

    public Path getPath() {
        return path;
    }

    public int getDefaultZone() {
        return zone;
    }

    public int getDefaultNet() {
        return net;
    }

    public Charset getCharset() {
        return charset;
    }

    public boolean isChecksumRequired() {
        return requireChecksum;
    }

    boolean isChecksumIgnored() {
        return ignoreChecksum;
    }

    DirectorySource withIgnoredChecksum(boolean ignored) {
        return new DirectorySource(id, format, path, zone, net, charset, requireChecksum, ignored);
    }

    DirectorySource atPath(Path candidate, boolean required) {
        return new DirectorySource(id,
                                   format,
                                   candidate,
                                   zone,
                                   net,
                                   charset,
                                   required,
                                   ignoreChecksum);
    }

    String definition() {
        return format
               + "|"
               + path
               + "|"
               + zone
               + "|"
               + net
               + "|"
               + charset.name()
               + "|"
               + requireChecksum
               + (ignoreChecksum ? "|ignored-header-crc" : "");
    }
}
