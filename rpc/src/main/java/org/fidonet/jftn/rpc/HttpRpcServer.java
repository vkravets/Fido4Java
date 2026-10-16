package org.fidonet.jftn.rpc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.transport.TIOStreamTransport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Optional HTTP/JSON transport. TLS can be terminated by a reverse proxy. */
public final class HttpRpcServer implements RpcTransport {
    private static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    private static final String JSON_TYPE = "application/vnd.apache.thrift.json";

    private final HttpServer server;
    private final ThreadPoolExecutor workers;
    private final Map<String, TProcessor> services;
    private final Set<String> origins;
    private boolean started;
    private boolean closed;

    public HttpRpcServer(int port, String bind, ServerHandler handlers, Set<String> origins)
            throws IOException {
        this.origins = Set.copyOf(origins);
        this.origins.forEach(HttpRpcServer::validateOrigin);
        services = RpcProcessors.services(handlers);
        Map<String, HttpHandler> routes = new LinkedHashMap<>();
        for (HttpRouteProvider provider : ServiceLoader.load(HttpRouteProvider.class)) {
            provider.routes().forEach((route, handler) -> {
                if (!route.startsWith("/") || route.startsWith("/rpc") || handler == null
                        || routes.putIfAbsent(route, handler) != null) {
                    throw new IllegalArgumentException("Invalid or duplicate HTTP route: " + route);
                }
            });
        }
        server = HttpServer.create(new InetSocketAddress(bind, port), 32);
        workers = new ThreadPoolExecutor(2,
                                         8,
                                         30,
                                         TimeUnit.SECONDS,
                                         new ArrayBlockingQueue<>(32),
                                         new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(workers);
        server.createContext("/rpc/", this::handle);
        routes.forEach(server::createContext);
    }

    private static void validateOrigin(String value) {
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !uri.getRawPath().isEmpty()) {
            throw new IllegalArgumentException("RpcHttpOrigin must be an exact HTTP(S) origin");
        }
    }

    public synchronized void run() {
        if (closed || started) {
            throw new IllegalStateException("HTTP RPC server cannot be started again");
        }
        server.start();
        started = true;
    }

    public int getLocalPort() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            handleRequest(exchange);
        } finally {
            exchange.close();
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (!allowOrigin(exchange)) {
            respond(exchange, 403, new byte[0]);
            return;
        }
        TProcessor processor = services.get(exchange.getRequestURI().getPath().substring(5));
        if (processor == null) {
            respond(exchange, 404, new byte[0]);
            return;
        }
        if (exchange.getRequestMethod().equals("OPTIONS")) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            respond(exchange, 204, new byte[0]);
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST, OPTIONS");
            respond(exchange, 405, new byte[0]);
            return;
        }
        if (!isThriftContentType(exchange)) {
            respond(exchange, 415, new byte[0]);
            return;
        }
        process(exchange, processor);
    }

    private boolean allowOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) {
            return true;
        }
        exchange.getResponseHeaders().set("Vary", "Origin");
        if (!origins.contains(origin) && !isLocalOrigin(origin)) {
            return false;
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        return true;
    }

    private boolean isLocalOrigin(String origin) {
        int port = getLocalPort();
        return server.getAddress().getAddress().isLoopbackAddress()
                && (origin.equals("http://localhost:" + port)
                        || origin.equals("http://127.0.0.1:" + port)
                        || origin.equals("http://[::1]:" + port));
    }

    private static boolean isThriftContentType(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst("Content-Type");
        if (value == null) {
            return false;
        }
        String mediaType = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.equals(JSON_TYPE) || mediaType.equals("application/x-thrift");
    }

    private static void process(HttpExchange exchange, TProcessor processor) throws IOException {
        byte[] request = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (request.length > MAX_REQUEST_BYTES) {
            respond(exchange, 413, new byte[0]);
            return;
        }
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        try {
            TIOStreamTransport transport = new TIOStreamTransport(new ByteArrayInputStream(request),
                                                                  response);
            processor.process(new TJSONProtocol(transport), new TJSONProtocol(transport));
        } catch (TException | IllegalArgumentException failure) {
            respond(exchange, 400, new byte[0]);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", JSON_TYPE + "; charset=utf-8");
        respond(exchange, 200, response.toByteArray());
    }

    private static void respond(HttpExchange exchange, int status, byte[] bytes)
            throws IOException {
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        // JDK HttpServer.stop() leaves a bound socket open if start() was never called.
        if (!started) {
            server.start();
        }
        server.stop(started ? 1 : 0);
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("HTTP RPC workers did not stop");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping HTTP RPC", failure);
        }
    }
}
