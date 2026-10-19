package org.fidonet.web;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.ServiceConfiguration;
import org.fidonet.jftn.rpc.HttpRouteProvider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Serves packaged Vue assets and SPA routes; never exposes server filesystem paths. */
public final class StaticUi implements HttpRouteProvider {
    @Override
    public Map<String, HttpHandler> routes(JFtnConfig config) {
        return ServiceConfiguration.flag(config, "WebUiEnabled", true) ? routes() : Map.of();
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
        exchange.getResponseHeaders().set("Content-Type", contentType(resource));
        exchange.getResponseHeaders()
                .set("Cache-Control",
                     resource.startsWith("/assets/")
                             ? "public, max-age=31536000, immutable"
                             : "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders()
                .set("Content-Security-Policy",
                     "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'");
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
