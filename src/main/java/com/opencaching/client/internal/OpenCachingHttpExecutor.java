package com.opencaching.client.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencaching.client.CdniMediaTypes;
import com.opencaching.client.OpenCachingConfiguration;
import com.opencaching.client.OpenCachingException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Bearer-authenticated HTTP for the Open Caching interfaces.
 * <p>
 * Two things here are specific to this API rather than boilerplate. First, the request
 * Content-Type is passed per call because CMI selects the trigger operation from the CDNI
 * {@code ptype} parameter. Second, several operations answer 304 Not Modified as a normal
 * outcome (trigger and bucket reads), so 304 is surfaced rather than treated as an error.
 */
public final class OpenCachingHttpExecutor {

    private final OpenCachingConfiguration config;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public OpenCachingHttpExecutor(OpenCachingConfiguration config, HttpClient http, ObjectMapper mapper) {
        this.config = config;
        this.http = http;
        this.mapper = mapper;
    }

    /** URL-encodes one path segment (bucket names and trigger ids are caller-supplied). */
    public static String segment(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public <T> T get(String path, Class<T> type) {
        return send(build("GET", path, null, null), type);
    }

    public <T> T post(String path, Object body, String contentType, Class<T> type) {
        return send(build("POST", path, serialize(body), contentType), type);
    }

    public <T> T put(String path, Object body, String contentType, Class<T> type) {
        return send(build("PUT", path, serialize(body), contentType), type);
    }

    public void delete(String path) {
        send(build("DELETE", path, null, null), Void.class);
    }

    /** Raw status + body, for callers that need to distinguish 200 from 304 or 201 from 200. */
    public Result exchange(String method, String path, Object body, String contentType) {
        HttpResponse<String> r = sendWithRetry(build(method, path, body == null ? null : serialize(body), contentType));
        if (isError(r.statusCode())) {
            throw new OpenCachingException(r.statusCode(), r.body());
        }
        return new Result(r.statusCode(), r.body(), r.headers().firstValue("ETag").orElse(null),
                r.headers().firstValue("Location").orElse(null));
    }

    public record Result(int status, String body, String etag, String location) {
        public boolean notModified() { return status == 304; }
    }

    public <T> T parse(String body, Class<T> type) {
        if (type == Void.class || body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(body, type);
        } catch (IOException e) {
            throw OpenCachingException.transport("Failed to parse Open Caching response: " + e.getMessage());
        }
    }

    private <T> T send(HttpRequest request, Class<T> type) {
        HttpResponse<String> r = sendWithRetry(request);
        if (isError(r.statusCode())) {
            throw new OpenCachingException(r.statusCode(), r.body());
        }
        return parse(r.body(), type);
    }

    private static boolean isError(int status) {
        // 3xx is protocol, not failure: 304 on CMI/FCI reads, 302/307 on RRI request-driven
        return status < 200 || status >= 400;
    }

    /**
     * 425 means the dCDN's routing state is not ready yet and 500/502/503/504 are transient.
     * 501 is not retried: "this dCDN does not implement this operation" is a permanent answer,
     * and CMI documents it for trigger modification specifically.
     */
    private static boolean isRetryable(int status) {
        return status == 425 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) {
        int attempt = 0;
        while (true) {
            HttpResponse<String> r = sendOnce(request);
            if (!isRetryable(r.statusCode()) || attempt >= config.maxRetries()) {
                return r;
            }
            attempt++;
            sleep(500L * (1L << (attempt - 1)));
        }
    }

    private HttpResponse<String> sendOnce(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw OpenCachingException.transport("I/O error calling Open Caching API: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OpenCachingException.transport("Interrupted calling Open Caching API");
        }
    }

    private HttpRequest build(String method, String path, String body, String contentType) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/" + (path.startsWith("/") ? path.substring(1) : path)))
                .timeout(config.requestTimeout())
                .header("Authorization", "Bearer " + config.bearerToken())
                .header("Accept", CdniMediaTypes.JSON);
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        if (body != null) {
            b.header("Content-Type", contentType == null ? CdniMediaTypes.JSON : contentType);
        }
        return b.method(method, publisher).build();
    }

    private String serialize(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to serialize request body", e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
