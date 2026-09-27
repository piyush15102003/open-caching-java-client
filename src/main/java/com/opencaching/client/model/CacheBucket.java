package com.opencaching.client.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

/**
 * {@code CMI.CacheBucket} — a named unit of cache or storage the uCDN manages on the dCDN.
 * {@code name} and {@code type} are the spec's required fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CacheBucket(
        String name,
        String id,
        Status status,
        Type type,
        Integer quota,
        List<JsonNode> metrics) {

    public CacheBucket {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(type, "type is required");
    }

    public static CacheBucket of(String name, Type type) {
        return new CacheBucket(name, null, null, type, null, null);
    }

    public CacheBucket withQuota(int quota) {
        return new CacheBucket(name, id, status, type, quota, metrics);
    }

    public enum Status {
        @JsonProperty("active") ACTIVE,
        @JsonProperty("idle") IDLE,
        @JsonProperty("degraded") DEGRADED,
        @JsonProperty("offline") OFFLINE,
        @JsonEnumDefaultValue UNRECOGNIZED
    }

    public enum Type {
        @JsonProperty("storage") STORAGE,
        @JsonProperty("cache") CACHE,
        @JsonEnumDefaultValue UNRECOGNIZED
    }
}
