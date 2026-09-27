package com.opencaching.client;

/**
 * Non-2xx response from an Open Caching endpoint, or a transport/parse failure
 * ({@code statusCode() == -1}).
 * <p>
 * The interfaces share a small set of meaningful codes: 403 where the uCDN is not authorised
 * for the bucket or footprint, 404 for an unknown trigger/bucket/subscription, 409 on a bucket
 * name/id conflict, 425 ("too early") on RRI, and 501 where the dCDN does not implement trigger
 * modification or deletion.
 */
public class OpenCachingException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public OpenCachingException(int statusCode, String responseBody) {
        super("Open Caching API error " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public static OpenCachingException transport(String message) {
        return new OpenCachingException(-1, message);
    }

    public int statusCode() { return statusCode; }
    public String responseBody() { return responseBody; }

    public boolean isUnauthorized() { return statusCode == 401 || statusCode == 403; }
    public boolean isNotFound() { return statusCode == 404; }
    public boolean isConflict() { return statusCode == 409; }
    /** 501: the dCDN advertises the interface but has not implemented this operation. */
    public boolean isNotImplemented() { return statusCode == 501; }
    /** 425 Too Early — RRI only; the request may be retried once routing state settles. */
    public boolean isTooEarly() { return statusCode == 425; }
    public boolean isRetryable() { return statusCode == 425 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504; }
}
