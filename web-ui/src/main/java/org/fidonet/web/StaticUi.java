package org.fidonet.web;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.ServiceConfiguration;
import org.fidonet.jftn.rpc.HttpRouteProvider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/** Serves packaged Vue assets and SPA routes; never exposes server filesystem paths. */
public final class StaticUi implements HttpRouteProvider {
    @Override
    public Map<String, HttpHandler> routes(JFtnConfig config) {
        if (!ServiceConfiguration.flag(config, "WebUiEnabled", true))
            return Map.of();
        Path directory = config.getSourcePath() == null
                ? Path.of(".")
                : config.getSourcePath().getParent();
        Path themes = directory.resolve("themes/mail");
        try {
            Files.createDirectories(themes);
        } catch (IOException failure) {
            LoggerFactory.getLogger(StaticUi.class)
                         .warn("Could not create mail theme directory", failure);
        }
        return Map.of("/", this::serve, "/ui/mail-themes.json", new MailThemeFiles(themes));
    }
    @Override
    public Map<String, HttpHandler> routes() {
        return Map.of("/", this::serve);
    }

    private void serve(HttpExchange exchange) throws IOException {
        try {
            respond(exchange);
        } finally {
            exchange.close();
        }
    }

    private void respond(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if (!(method.equals("GET") || method.equals("HEAD")) || path.contains("..")
                || path.startsWith("/rpc") || path.length() > 4096) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        String resource = path.equals("/")
                || !path.substring(path.lastIndexOf('/') + 1).contains(".") ? "/index.html" : path;
        try (InputStream input = StaticUi.class.getResourceAsStream("/web-ui" + resource)) {
            sendResource(exchange, input, resource, method.equals("HEAD"));
        }
    }

    private static void sendResource(HttpExchange exchange,
                                     InputStream input,
                                     String resource,
                                     boolean head)
            throws IOException {
        if (input == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        byte[] bytes = input.readAllBytes();
        byte[] random = new byte[18];
        new SecureRandom().nextBytes(random);
        String nonce = Base64.getEncoder().encodeToString(random);
        if (resource.equals("/index.html")) {
            String html = new String(bytes, StandardCharsets.UTF_8);
            bytes = html.replace("<head>",
                                 "<head><meta name=\"jftn-style-nonce\" content=\"" + nonce + "\">")
                        .getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", contentType(resource));
        exchange.getResponseHeaders()
                .set("Cache-Control",
                     resource.startsWith("/assets/")
                             ? "public, max-age=31536000, immutable"
                             : "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders()
                .set("Content-Security-Policy",
                     "default-src 'self'; script-src 'self'; style-src 'self' 'nonce-" + nonce
                             + "'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'");
        exchange.sendResponseHeaders(200, head ? -1 : bytes.length);
        if (!head) {
            exchange.getResponseBody().write(bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".js") || path.endsWith(".mjs")) {
            return "text/javascript; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "text/html; charset=utf-8";
    }
}
