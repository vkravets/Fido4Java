package org.fidonet.fileecho;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** HTick Replaces removes matches from the active area; retained copies stay outside its index. */
final class FileEchoReplacements {
    private FileEchoReplacements() {
    }

    static String mask(String value) throws IOException {
        if (value.isEmpty() || value.startsWith("*")) {
            return "";
        }
        if (!value.matches("[A-Za-z0-9!#$%&'()@^_`{}~?*.-]{1,12}") || value.contains("..")) {
            throw new IOException("Invalid TIC Replaces mask");
        }
        return value;
    }

    static void apply(Path directory, String mask, String receipt) throws IOException {
        if (mask.isEmpty() || !Files.isDirectory(directory)) {
            return;
        }
        if (!receipt.matches("[0-9a-f]{64}") || Files.isSymbolicLink(directory)) {
            throw new IOException("Unsafe fileecho replacement directory or receipt");
        }
        Pattern pattern = pattern(mask);
        List<Path> matches;
        try (Stream<Path> files = Files.list(directory)) {
            matches = files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                           .filter(path -> !protectedName(path.getFileName().toString()))
                           .filter(path -> pattern.matcher(path.getFileName().toString()).matches())
                           .collect(Collectors.toList());
        }
        if (matches.isEmpty()) {
            return;
        }
        Path archive = archive(directory, receipt);
        for (Path file : matches) {
            Files.move(file, archive.resolve(file.getFileName()));
        }
    }

    private static Path archive(Path directory, String receipt) throws IOException {
        Path archive = directory.resolve("dupes").resolve(receipt).resolve("replaced");
        Path component = directory;
        for (Path name : directory.relativize(archive)) {
            component = component.resolve(name);
            if (Files.isSymbolicLink(component)) {
                throw new IOException("Replacement archive cannot contain symbolic links");
            }
            Files.createDirectories(component);
        }
        return archive;
    }

    private static boolean protectedName(String name) {
        return name.startsWith(".")
                || Set.of("file.bbs", "files.bbs").contains(name.toLowerCase(Locale.ROOT));
    }

    private static Pattern pattern(String mask) {
        StringBuilder expression = new StringBuilder();
        for (char character : mask.toCharArray()) {
            expression.append(character == '*'
                    ? ".*"
                    : character == '?' ? "." : Pattern.quote(String.valueOf(character)));
        }
        return Pattern.compile(expression.toString(), Pattern.CASE_INSENSITIVE);
    }
}
