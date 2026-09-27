package com.opencaching.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencaching.client.internal.OpenCachingHttpExecutor;
import com.opencaching.client.internal.OpenCachingHttpExecutor.Result;
import com.opencaching.client.internal.OpenCachingObjectMapper;
import com.opencaching.client.model.BucketIdType;
import com.opencaching.client.model.CacheBucket;
import com.opencaching.client.model.RoutingRequest;
import com.opencaching.client.model.RoutingResponse;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static com.opencaching.client.internal.OpenCachingHttpExecutor.segment;

/**
 * Java client for the SVTA Open Caching API, written from the OpenAPI documents published at
 * https://github.com/streaming-video-technology-alliance/OC-API (branch {@code COI_v2.1}, MIT).
 *
 * <p>Covers the three interfaces that define HTTP operations:
 * <ul>
 *   <li><b>CMI</b> — Cache Management Interface: cache buckets and CI triggers</li>
 *   <li><b>FCI</b> — Footprint and Capabilities Interface: advertisements and update subscriptions</li>
 *   <li><b>RRI</b> — Request Routing Interface: recursive redirection requests</li>
 * </ul>
 * CII (Capacity Insight) is schema-only in the spec — it contributes FCI objects rather than
 * endpoints — so there is nothing to call there.
 *
 * <pre>{@code
 * var client = OpenCachingClient.create("https://dcdn.example.net", token);
 * client.cmi().createBucket(CacheBucket.of("live-eu-west", CacheBucket.Type.CACHE).withQuota(500));
 * JsonNode advert = client.fci().getAdvertisement();
 * }</pre>
 */
public final class OpenCachingClient {

    private final OpenCachingHttpExecutor http;
    private final Cmi cmi = new Cmi();
    private final Fci fci = new Fci();
    private final Rri rri = new Rri();

    OpenCachingClient(OpenCachingHttpExecutor http) {
        this.http = http;
    }

    public static OpenCachingClient create(String baseUrl, String bearerToken) {
        return create(OpenCachingConfiguration.builder(baseUrl, bearerToken).build());
    }

    public static OpenCachingClient create(OpenCachingConfiguration config) {
        return create(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public static OpenCachingClient create(OpenCachingConfiguration config, HttpClient httpClient) {
        return new OpenCachingClient(new OpenCachingHttpExecutor(config, httpClient, OpenCachingObjectMapper.create()));
    }

    public Cmi cmi() { return cmi; }
    public Fci fci() { return fci; }
    public Rri rri() { return rri; }

    /** Cache Management Interface — {@code /OC/CMI/...}. */
    public final class Cmi {

        // ---- buckets ---------------------------------------------------------------------

        /** {@code PUT /OC/CMI/bucket/{bucketNameOrId}} — create (201) or replace (200) a bucket. */
        public CacheBucket createBucket(CacheBucket bucket) {
            Result r = http.exchange("PUT", "/OC/CMI/bucket/" + segment(bucket.name()), bucket, CdniMediaTypes.JSON);
            return http.parse(r.body(), CacheBucket.class);
        }

        /** {@code POST /OC/CMI/bucket/{bucketNameOrId}} — modify an existing bucket. */
        public CacheBucket modifyBucket(String bucketNameOrId, CacheBucket bucket) {
            return http.post("/OC/CMI/bucket/" + segment(bucketNameOrId), bucket, CdniMediaTypes.JSON, CacheBucket.class);
        }

        /** {@code GET /OC/CMI/bucket/{bucketNameOrId}}. */
        public CacheBucket getBucket(String bucketNameOrId) {
            return http.get("/OC/CMI/bucket/" + segment(bucketNameOrId), CacheBucket.class);
        }

        /** {@code GET /OC/CMI/bucket/} — all buckets visible to this uCDN. */
        public List<CacheBucket> listBuckets() {
            CacheBucket[] all = http.get("/OC/CMI/bucket/", CacheBucket[].class);
            return all == null ? List.of() : List.of(all);
        }

        /** {@code DELETE /OC/CMI/bucket/{bucketNameOrId}} — 409 if the bucket is still in use. */
        public void deleteBucket(String bucketNameOrId) {
            http.delete("/OC/CMI/bucket/" + segment(bucketNameOrId));
        }

        // ---- triggers --------------------------------------------------------------------

        /**
         * {@code POST /OC/CMI/{bucketIdType}/{bucketId}/V2_14/triggers} — create a CI trigger
         * (preposition, purge or invalidate) so the dCDN fetches or drops content.
         *
         * <p>The body is passed as JSON rather than a typed record on purpose: the
         * {@code ci-trigger-command.create.v2} schema is defined in the separate Trigger
         * document, not in the CMI document this client was written from. The Content-Type,
         * which is what actually selects the operation, is set correctly.
         */
        public JsonNode createTrigger(BucketIdType idType, String bucketId, JsonNode command) {
            String path = "/OC/CMI/" + idType.segment() + "/" + segment(bucketId) + "/V2_14/triggers";
            return http.post(path, command, CdniMediaTypes.CI_TRIGGER_COMMAND_CREATE_V2, JsonNode.class);
        }

        /** {@code GET /OC/CMI/{bucketIdType}/{bucketId}/V2_14/triggers} — all trigger collections. */
        public JsonNode listTriggers(BucketIdType idType, String bucketId) {
            String path = "/OC/CMI/" + idType.segment() + "/" + segment(bucketId) + "/V2_14/triggers";
            return http.get(path, JsonNode.class);
        }

        /** {@code GET /OC/CMI/{bucketIdType}/{bucketId}/triggers/{triggerId}}. */
        public JsonNode getTrigger(BucketIdType idType, String bucketId, String triggerId) {
            return http.get(triggerPath(idType, bucketId, triggerId), JsonNode.class);
        }

        /**
         * {@code POST /OC/CMI/{bucketIdType}/{bucketId}/triggers/{triggerId}} — modify a trigger.
         * A dCDN that does not support modification answers 501, surfaced as
         * {@link OpenCachingException#isNotImplemented()}.
         */
        public JsonNode modifyTrigger(BucketIdType idType, String bucketId, String triggerId, JsonNode command) {
            return http.post(triggerPath(idType, bucketId, triggerId), command,
                    CdniMediaTypes.CI_TRIGGER_COMMAND_MODIFY_V2, JsonNode.class);
        }

        /** {@code DELETE /OC/CMI/{bucketIdType}/{bucketId}/triggers/{triggerId}}. */
        public void deleteTrigger(BucketIdType idType, String bucketId, String triggerId) {
            http.delete(triggerPath(idType, bucketId, triggerId));
        }

        private String triggerPath(BucketIdType idType, String bucketId, String triggerId) {
            return "/OC/CMI/" + idType.segment() + "/" + segment(bucketId) + "/triggers/" + segment(triggerId);
        }
    }

    /** Footprint and Capabilities Interface — {@code /OC/FCI/...}. */
    public final class Fci {

        /**
         * {@code GET /OC/FCI/advertisement} — the dCDN's footprint and capability objects per
         * SVTA and CDNI (RFC 8008). Returned as JSON: the advertisement is an open-ended list of
         * capability objects whose members are extended by each SVTA revision.
         */
        public JsonNode getAdvertisement() {
            return http.get("/OC/FCI/advertisement", JsonNode.class);
        }

        /** {@code POST /OC/FCI/subscription} — subscribe to FCI update notifications (201). */
        public JsonNode subscribe(JsonNode subscription) {
            return http.post("/OC/FCI/subscription", subscription, CdniMediaTypes.CDNI, JsonNode.class);
        }

        /** {@code GET /OC/FCI/subscription/{subscriptionId}}. */
        public JsonNode getSubscription(String subscriptionId) {
            return http.get("/OC/FCI/subscription/" + segment(subscriptionId), JsonNode.class);
        }

        /** {@code DELETE /OC/FCI/subscription/{subscriptionId}}. */
        public void deleteSubscription(String subscriptionId) {
            http.delete("/OC/FCI/subscription/" + segment(subscriptionId));
        }
    }

    /** Request Routing Interface — {@code /OC/RRI/...}. */
    public final class Rri {

        /**
         * {@code POST /OC/RRI/routing_request} — ask the dCDN where to send a client, for the
         * DNS or HTTP redirect method in recursive mode.
         */
        public RoutingResponse routingRequest(RoutingRequest request) {
            return http.post("/OC/RRI/routing_request", request,
                    CdniMediaTypes.REDIRECTION_REQUEST, RoutingResponse.class);
        }

        /**
         * {@code GET /OC/RRI/{object}} — request-driven delegation. The dCDN normally answers
         * 302/307 with a Location; this returns the raw result so the caller can decide whether
         * to follow it rather than having the HTTP client follow it silently.
         */
        public Result requestDriven(String object) {
            return http.exchange("GET", "/OC/RRI/" + segment(object), null, null);
        }
    }
}
