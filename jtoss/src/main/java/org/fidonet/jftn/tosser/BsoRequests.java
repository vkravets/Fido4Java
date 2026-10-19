package org.fidonet.jftn.tosser;

import org.fidonet.misc.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** HPT processRequests writes space/tab-separated subject tokens as CRLF request lines. */
final class BsoRequests {
    private BsoRequests() {
    }

    static String normalize(String subject) throws IOException {
        if (subject == null || subject.isBlank() || subject.length() > 8192
                || subject.chars().anyMatch(value -> value < 32 && value != 9 || value == 127)) {
            throw new IOException("Invalid file request subject");
        }
        List<String> tokens = Arrays.asList(subject.trim().split("[ \\t]+"));
        if (tokens.size() > 128 || tokens.stream().anyMatch(BsoRequests::unsafe)) {
            throw new IOException("File requests require filename masks without paths");
        }
        return String.join("\r\n", tokens) + "\r\n";
    }

    private static boolean unsafe(String token) {
        return token.contains("/") || token.contains("\\") || token.contains(":")
                || token.equals(".") || token.equals("..");
    }

    static void append(Path request, String content) throws IOException {
        if (Files.isSymbolicLink(request) || Files.exists(request, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(request) > 1024 * 1024)) {
            throw new IOException("Unsafe or oversized BSO request file");
        }
        List<String> lines = Files.exists(request)
                ? new ArrayList<>(Files.readAllLines(request, StandardCharsets.UTF_8))
                : new ArrayList<>();
        for (String token : content.split("\r\n")) {
            if (!lines.contains(token)) {
                lines.add(token);
            }
        }
        AtomicFiles.replace(request,
                            output -> output.write((String.join("\r\n", lines)
                                    + "\r\n").getBytes(StandardCharsets.UTF_8)));
    }
}
