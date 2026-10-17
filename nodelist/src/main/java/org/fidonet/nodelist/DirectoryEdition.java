package org.fidonet.nodelist;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads publication dates for ordering; the importer still validates the complete payload. */
public final class DirectoryEdition {
    private static final Pattern DATE = Pattern.compile("\\b((?:January|February|March|April|May|June|July|August|September|October|November|December)"
            + " +[0-9]{1,2}, +[0-9]{4}) +-- +Day number +([0-9]{1,3}) *:",
                                                        Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder().parseCaseInsensitive()
                                                                                  .appendPattern("MMMM d, uuuu")
                                                                                  .toFormatter(Locale.ENGLISH)
                                                                                  .withResolverStyle(ResolverStyle.STRICT);

    private DirectoryEdition() {
    }

    public static Optional<LocalDate> read(Path path) throws IOException {
        try (InputStream raw = Files.newInputStream(path);
                DirectoryInput input = DirectoryInput.open(raw)) {
            String prefix = new String(input.content().readNBytes(4096), StandardCharsets.US_ASCII);
            String header = prefix.split("[\r\n]", 2)[0];
            if (!header.startsWith(";")) {
                return Optional.empty();
            }
            return parse(header);
        }
    }

    private static Optional<LocalDate> parse(String header) throws IOException {
        Matcher matcher = DATE.matcher(header);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            LocalDate date = LocalDate.parse(matcher.group(1).replaceAll(" +", " "), FORMAT);
            if (date.getDayOfYear() != Integer.parseInt(matcher.group(2))) {
                throw new IOException("Directory publication date and day number disagree");
            }
            return Optional.of(date);
        } catch (DateTimeParseException failure) {
            throw new IOException("Invalid directory publication date", failure);
        }
    }
}
