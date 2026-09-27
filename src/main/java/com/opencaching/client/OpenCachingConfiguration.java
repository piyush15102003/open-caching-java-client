package com.opencaching.client;

import java.time.Duration;
import java.util.Objects;

/**
 * Connection settings for a dCDN's Open Caching endpoint.
 * <p>
 * The SVTA OpenAPI documents intentionally declare no {@code servers} block — each ISP/dCDN
 * hosts the interfaces itself and the uCDN is told the base URL out of band — so the base URL
 * is always caller-supplied. Security is {@code bearerToken} in all three interfaces.
 */
public final class OpenCachingConfiguration {

    private final String baseUrl;
    private final String bearerToken;
    private final Duration requestTimeout;
    private final int maxRetries;

    private OpenCachingConfiguration(Builder b) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(b.baseUrl, "baseUrl"));
        this.bearerToken = Objects.requireNonNull(b.bearerToken, "bearerToken");
        this.requestTimeout = Objects.requireNonNull(b.requestTimeout, "requestTimeout");
        this.maxRetries = b.maxRetries;
    }

    public static Builder builder(String baseUrl, String bearerToken) {
        return new Builder(baseUrl, bearerToken);
    }

    public String baseUrl() { return baseUrl; }
    public String bearerToken() { return bearerToken; }
    public Duration requestTimeout() { return requestTimeout; }
    /** Retries on 425/5xx with exponential back-off; 0 disables retrying. */
    public int maxRetries() { return maxRetries; }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public static final class Builder {
        private final String baseUrl;
        private final String bearerToken;
        private Duration requestTimeout = Duration.ofSeconds(30);
        private int maxRetries = 2;

        private Builder(String baseUrl, String bearerToken) {
            this.baseUrl = baseUrl;
            this.bearerToken = bearerToken;
        }

        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenCachingConfiguration build() { return new OpenCachingConfiguration(this); }
    }
}
