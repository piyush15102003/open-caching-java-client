package com.opencaching.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code RI.Response} — the redirection answer. The {@code dns}, {@code http}, {@code scope}
 * and {@code error} members are left as JSON: each expands into a family of RI_* schemas whose
 * shape depends on the redirection method negotiated, and typing them blind would be guesswork.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoutingResponse(JsonNode dns, JsonNode http, JsonNode scope, JsonNode error) {

    public boolean isError() {
        return error != null && !error.isNull();
    }
}
