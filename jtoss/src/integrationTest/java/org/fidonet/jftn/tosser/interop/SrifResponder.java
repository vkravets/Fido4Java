package org.fidonet.jftn.tosser.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal test-only SRIF service, not a production request processor or arbitrary file server. */
public final class SrifResponder {
    private SrifResponder() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("/data/scenario");
        Path srif = Path.of(args[0]);
        assertTrue(Files.size(srif) < 65536);
        Map<String, String> fields = new HashMap<>();
        for (String line : Files.readAllLines(srif, StandardCharsets.US_ASCII)) {
            String[] pair = line.split(" ", 2);
            if (pair.length == 2) {
                fields.put(pair[0], pair[1].trim());
            }
        }
        assertEquals("PROTECTED", fields.get("RemoteStatus"));
        assertTrue(fields.get("AKA").startsWith("1:500/100"));
        Path requests = Path.of(fields.get("RequestList"));
        Path response = Path.of(fields.get("ResponseList"));
        assertTrue(Files.size(requests) < 1024);
        List<String> names = Files.readAllLines(requests, StandardCharsets.US_ASCII);
        assertTrue(names.equals(List.of("ANSWER", "NOTHERE")) || names.equals(List.of("NOTHERE")));
        Files.copy(srif, root.resolve("logs/request.srif"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(requests, root.resolve("logs/request.txt"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(root.resolve("logs/request.calls"),
                          "handled\n",
                          StandardOpenOption.CREATE,
                          StandardOpenOption.APPEND);
        String answer = names.contains("ANSWER")
                ? "+" + root.resolve("request-files/ANSWER.ZIP") + "\r\n"
                : "";
        Files.writeString(response, answer, StandardCharsets.US_ASCII);
    }
}
