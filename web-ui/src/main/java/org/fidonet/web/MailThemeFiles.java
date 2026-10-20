package org.fidonet.web;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Public theme assets only; never serves arbitrary files or operator configuration. */
final class MailThemeFiles implements HttpHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MailThemeFiles.class);
    private static final List<String> COLORS = List.of("background",
                                                       "text",
                                                       "quote1",
                                                       "quote2",
                                                       "quote3",
                                                       "ending",
                                                       "link",
                                                       "selection",
                                                       "search",
                                                       "caret");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
                                                                         .streamReadConstraints(StreamReadConstraints.builder()
                                                                                                                     .maxNestingDepth(8)
                                                                                                                     .maxStringLength(1000)
                                                                                                                     .build())
                                                                         .build());
    private final Path root;

    MailThemeFiles(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    List<Map<String, Object>> read() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            return List.of();
        List<Path> files;
        try (Stream<Path> entries = Files.list(root)) {
            files = entries.filter(path -> path.getFileName()
                                               .toString()
                                               .matches("[A-Za-z0-9_-]+\\.json"))
                           .sorted()
                           .limit(32)
                           .collect(Collectors.toList());
        }
        List<Map<String, Object>> themes = new ArrayList<>();
        for (Path file : files) {
            try {
                Map<String, Object> theme = theme(file);
                if (theme != null)
                    themes.add(theme);
            } catch (Exception failure) {
                LOG.warn("Ignored invalid mail theme {}", file.getFileName());
            }
        }
        return themes;
    }

    private Map<String, Object> theme(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 32768)
            return null;
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file,
                                                      StandardOpenOption.READ,
                                                      LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(32769);
        }
        if (bytes.length > 32768)
            return null;
        JsonNode node = JSON.readTree(bytes);
        String id = text(node, "id");
        String name = text(node, "name");
        if (!id.matches("[a-z0-9_-]{1,48}") || name.isBlank() || name.length() > 80)
            return null;
        Map<String, String> colors = new LinkedHashMap<>();
        for (String key : COLORS) {
            String color = text(node.path("colors"), key);
            if (!color.matches("#[a-fA-F0-9]{6}"))
                return null;
            colors.put(key, color);
        }
        return Map.of("id", id, "name", name, "colors", colors);
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value != null && value.isTextual() ? value.textValue() : "";
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestURI().getPath().equals("/ui/mail-themes.json")
                    || !exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = JSON.writeValueAsBytes(read());
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }
}
