package com.opencaching.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

/**
 * {@code RI.Request} — body of {@code POST /OC/RRI/routing_request}. {@code cdn-path} is the
 * spec's only required field; it carries the chain of CDN identifiers the request has already
 * traversed and is what {@code max-hops} is checked against to stop redirection loops.
 * <p>
 * Kebab-case on the wire ({@code cdn-path}, {@code max-hops}) is handled by the shared
 * ObjectMapper naming strategy, not by per-field annotations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoutingRequest(JsonNode dns, JsonNode http, List<String> cdnPath, Integer maxHops) {

    public RoutingRequest {
        Objects.requireNonNull(cdnPath, "cdn-path is required");
        if (cdnPath.isEmpty()) {
            throw new IllegalArgumentException("cdn-path must not be empty");
        }
    }

    public static RoutingRequest forPath(List<String> cdnPath) {
        return new RoutingRequest(null, null, cdnPath, null);
    }

    public RoutingRequest withMaxHops(int maxHops) {
        return new RoutingRequest(dns, http, cdnPath, maxHops);
    }
}
