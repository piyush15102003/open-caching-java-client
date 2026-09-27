package com.opencaching.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencaching.client.internal.OpenCachingHttpExecutor;
import com.opencaching.client.model.BucketIdType;
import com.opencaching.client.model.CacheBucket;
import com.opencaching.client.model.RoutingRequest;
import com.opencaching.client.model.RoutingResponse;
import com.opencaching.client.testutil.MockOpenCachingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against an in-process stub. Payload shapes come from the SVTA OpenAPI documents in
 * streaming-video-technology-alliance/OC-API (branch COI_v2.1); no live dCDN is involved.
 */
class OpenCachingClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MockOpenCachingServer server;
    private OpenCachingClient client;

    @BeforeEach
    void setUp() {
        server = new MockOpenCachingServer();
        client = OpenCachingClient.create(
                OpenCachingConfiguration.builder(server.baseUrl(), "tok-123").maxRetries(1).build());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void createBucketSendsBearerAndKebabCaseBodyAndParsesEnums() throws Exception {
        server.enqueue(201, """
                {"name": "live-eu-west", "id": "b-91", "status": "active", "type": "cache", "quota": 500}
                """);

        CacheBucket created = client.cmi().createBucket(
                CacheBucket.of("live-eu-west", CacheBucket.Type.CACHE).withQuota(500));

        MockOpenCachingServer.RecordedRequest req = server.lastRequest();
        assertThat(req.method()).isEqualTo("PUT");
        assertThat(req.path()).isEqualTo("/OC/CMI/bucket/live-eu-west");
        assertThat(req.authToken()).isEqualTo("Bearer tok-123");
        assertThat(req.contentType()).isEqualTo("application/json");
        JsonNode body = JSON.readTree(req.body());
        assertThat(body.get("name").asText()).isEqualTo("live-eu-west");
        assertThat(body.get("type").asText()).isEqualTo("cache");
        assertThat(body.get("quota").asInt()).isEqualTo(500);
        assertThat(body.has("id")).as("nulls omitted").isFalse();

        assertThat(created.id()).isEqualTo("b-91");
        assertThat(created.status()).isEqualTo(CacheBucket.Status.ACTIVE);
        assertThat(created.type()).isEqualTo(CacheBucket.Type.CACHE);
    }

    @Test
    void bucketRequiresNameAndTypeAndToleratesUnknownEnumValues() {
        assertThatThrownBy(() -> new CacheBucket(null, null, null, CacheBucket.Type.CACHE, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CacheBucket("x", null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);

        server.enqueue(200, "{\"name\": \"b\", \"type\": \"hybrid-tier\", \"status\": \"draining\"}");
        CacheBucket b = client.cmi().getBucket("b");
        assertThat(b.type()).isEqualTo(CacheBucket.Type.UNRECOGNIZED);
        assertThat(b.status()).isEqualTo(CacheBucket.Status.UNRECOGNIZED);
    }

    @Test
    void listBucketsParsesArrayAndDeleteSendsNoBody() {
        server.enqueue(200, """
                [{"name": "a", "type": "cache"}, {"name": "b", "type": "storage", "quota": 10}]
                """);
        List<CacheBucket> all = client.cmi().listBuckets();
        assertThat(all).extracting(CacheBucket::name).containsExactly("a", "b");
        assertThat(all.get(1).type()).isEqualTo(CacheBucket.Type.STORAGE);
        assertThat(server.lastRequest().path()).isEqualTo("/OC/CMI/bucket/");

        server.enqueue(204, "");
        client.cmi().deleteBucket("a");
        assertThat(server.lastRequest().method()).isEqualTo("DELETE");
        assertThat(server.lastRequest().body()).isEmpty();
    }

    @Test
    void createTriggerUsesTheCdniPtypeContentTypeAndV2_14Path() throws Exception {
        server.enqueue(201, "{\"trigger-id\": \"t-1\", \"status\": \"pending\"}");
        JsonNode command = JSON.readTree("""
                {"cdn-path": ["ucdn.example"], "trigger": {"type": "preposition", "urls": ["https://cdn.example/a.ts"]}}
                """);

        JsonNode res = client.cmi().createTrigger(BucketIdType.NAME, "live eu/west", command);

        MockOpenCachingServer.RecordedRequest req = server.lastRequest();
        assertThat(req.method()).isEqualTo("POST");
        // bucket segment is URL-encoded; the V2_14 collection path is per the CMI spec
        assertThat(req.path()).isEqualTo("/OC/CMI/bucketName/live%20eu%2Fwest/V2_14/triggers");
        assertThat(req.contentType()).isEqualTo("application/cdni;ptype=ci-trigger-command.create.v2");
        assertThat(res.get("trigger-id").asText()).isEqualTo("t-1");
    }

    @Test
    void modifyTriggerUsesModifyPtypeAndSurfaces501AsNotImplemented() {
        server.enqueue(501, "{\"detail\": \"trigger modification unsupported\"}");

        assertThatThrownBy(() -> client.cmi().modifyTrigger(BucketIdType.ID, "b-91", "t-1", JSON.createObjectNode()))
                .isInstanceOf(OpenCachingException.class)
                .satisfies(e -> {
                    OpenCachingException ex = (OpenCachingException) e;
                    assertThat(ex.isNotImplemented()).isTrue();
                    assertThat(ex.isRetryable()).isFalse();
                });
        MockOpenCachingServer.RecordedRequest req = server.lastRequest();
        assertThat(req.path()).isEqualTo("/OC/CMI/bucketId/b-91/triggers/t-1");
        assertThat(req.contentType()).isEqualTo("application/cdni;ptype=ci-trigger-command.modify.v2");
    }

    @Test
    void notModifiedIsASuccessNotAnError() {
        // CMI and FCI reads document 304 as a normal outcome
        server.enqueue(304, "");
        JsonNode advert = client.fci().getAdvertisement();
        assertThat(advert).isNull();
        assertThat(server.lastRequest().path()).isEqualTo("/OC/FCI/advertisement");
    }

    @Test
    void fciSubscriptionLifecycle() {
        server.enqueue(201, "{\"subscription-id\": \"s-7\"}");
        JsonNode created = client.fci().subscribe(JSON.createObjectNode().put("callback", "https://ucdn.example/hook"));
        assertThat(created.get("subscription-id").asText()).isEqualTo("s-7");
        assertThat(server.lastRequest().contentType()).isEqualTo("application/cdni");

        server.enqueue(200, "{\"subscription-id\": \"s-7\", \"active\": true}");
        assertThat(client.fci().getSubscription("s-7").get("active").asBoolean()).isTrue();
        assertThat(server.lastRequest().path()).isEqualTo("/OC/FCI/subscription/s-7");

        server.enqueue(200, "");
        client.fci().deleteSubscription("s-7");
        assertThat(server.lastRequest().method()).isEqualTo("DELETE");
    }

    @Test
    void routingRequestSerialisesKebabCaseAndRejectsEmptyCdnPath() throws Exception {
        server.enqueue(200, """
                {"http": {"host": "cache1.dcdn.example"}, "scope": {"ipv4": "203.0.113.0/24"}}
                """);

        RoutingResponse res = client.rri().routingRequest(
                RoutingRequest.forPath(List.of("ucdn.example", "dcdn.example")).withMaxHops(3));

        MockOpenCachingServer.RecordedRequest req = server.lastRequest();
        assertThat(req.path()).isEqualTo("/OC/RRI/routing_request");
        assertThat(req.contentType()).isEqualTo("application/cdni;ptype=redirection-request");
        JsonNode body = JSON.readTree(req.body());
        assertThat(body.get("cdn-path")).hasSize(2);
        assertThat(body.get("max-hops").asInt()).isEqualTo(3);
        assertThat(body.has("dns")).as("nulls omitted").isFalse();

        assertThat(res.isError()).isFalse();
        assertThat(res.http().get("host").asText()).isEqualTo("cache1.dcdn.example");

        assertThatThrownBy(() -> RoutingRequest.forPath(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestDrivenReturnsRedirectWithoutFollowingIt() {
        server.enqueue(307, "", "Location", "https://cache1.dcdn.example/segment.ts");
        OpenCachingHttpExecutor.Result r = client.rri().requestDriven("segment.ts");
        assertThat(r.status()).isEqualTo(307);
        assertThat(r.location()).isEqualTo("https://cache1.dcdn.example/segment.ts");
    }

    @Test
    void retriesOn425TooEarlyThenSucceeds() {
        server.enqueue(425, "{\"detail\": \"routing state not ready\"}");
        server.enqueue(200, "{\"http\": {\"host\": \"cache2.dcdn.example\"}}");

        RoutingResponse res = client.rri().routingRequest(RoutingRequest.forPath(List.of("ucdn.example")));

        assertThat(res.http().get("host").asText()).isEqualTo("cache2.dcdn.example");
        assertThat(server.requests()).hasSize(2);
    }

    @Test
    void conflictAndForbiddenAreDistinguishable() {
        server.enqueue(409, "{\"detail\": \"bucket in use\"}");
        assertThatThrownBy(() -> client.cmi().deleteBucket("a"))
                .isInstanceOf(OpenCachingException.class)
                .satisfies(e -> assertThat(((OpenCachingException) e).isConflict()).isTrue());

        server.enqueue(403, "{\"detail\": \"not authorised for this footprint\"}");
        assertThatThrownBy(() -> client.fci().getAdvertisement())
                .isInstanceOf(OpenCachingException.class)
                .satisfies(e -> assertThat(((OpenCachingException) e).isUnauthorized()).isTrue());
    }
}
