package com.opencaching.client.testutil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-process HTTP server (JDK built-in, no extra dependency) that serves scripted responses and
 * records what the client actually sent. Supports response headers so redirect (Location) and
 * conditional (ETag) behaviour can be exercised.
 */
public final class MockOpenCachingServer implements AutoCloseable {

    public record RecordedRequest(String method, String path, String query, String authToken,
                                  String accept, String contentType, String body) {
    }

    private record Stub(int status, String body, Map<String, String> headers) {
    }

    private final HttpServer server;
    private final Deque<Stub> queued = new ArrayDeque<>();
    private final List<RecordedRequest> recorded = new ArrayList<>();

    public MockOpenCachingServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public void enqueue(int status, String body) {
        queued.addLast(new Stub(status, body, Map.of()));
    }

    /** Enqueue with response headers, e.g. {@code enqueue(307, "", "Location", "https://...")}. */
    public void enqueue(int status, String body, String... headerPairs) {
        if (headerPairs.length % 2 != 0) {
            throw new IllegalArgumentException("headerPairs must be name/value pairs");
        }
        Map<String, String> h = new LinkedHashMap<>();
        for (int i = 0; i < headerPairs.length; i += 2) {
            h.put(headerPairs[i], headerPairs[i + 1]);
        }
        queued.addLast(new Stub(status, body, h));
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<RecordedRequest> requests() {
        return recorded;
    }

    public RecordedRequest lastRequest() {
        return recorded.get(recorded.size() - 1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        recorded.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(),
                exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Accept"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                body));

        Stub stub = queued.isEmpty()
                ? new Stub(500, "{\"detail\":\"no stub queued\"}", Map.of())
                : queued.pollFirst();

        stub.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        byte[] bytes = stub.body() == null ? new byte[0] : stub.body().getBytes(StandardCharsets.UTF_8);
        boolean bodyAllowed = stub.status() != 204 && stub.status() != 304 && bytes.length > 0;
        if (bodyAllowed) {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
        }
        exchange.sendResponseHeaders(stub.status(), bodyAllowed ? bytes.length : -1);
        if (bodyAllowed) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
