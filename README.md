# open-caching-java-client

An unofficial Java 17 client for the **SVTA Open Caching API** — the delegation protocol a
uCDN (content provider CDN) uses to drive a dCDN (an ISP-operated caching layer such as
Velocix Open Caching).

It is written directly from the OpenAPI documents published by the Streaming Video Technology
Alliance at
[streaming-video-technology-alliance/OC-API](https://github.com/streaming-video-technology-alliance/OC-API)
(branch `COI_v2.1`, MIT licensed). There is no official Java client; the specs ship as YAML only.

## What it covers

Three of the four Open Caching interfaces define HTTP operations, and all three are implemented:

| Interface | Base path | Operations |
|---|---|---|
| **CMI** — Cache Management | `/OC/CMI` | bucket create/replace, modify, get, list, delete; CI trigger create, list, get, modify, delete |
| **FCI** — Footprint & Capabilities | `/OC/FCI` | advertisement; subscription create, get, delete |
| **RRI** — Request Routing | `/OC/RRI` | `routing_request` (recursive redirection), request-driven delegation |

**CII** (Capacity Insight Interface) is deliberately absent: in `COI_v2.1` it is a schema-only
document — it contributes capacity objects that are carried inside FCI advertisements and
defines no paths of its own, so there is nothing to call.

## Getting started

```xml
<dependency>
  <groupId>com.opencaching</groupId>
  <artifactId>open-caching-java-client</artifactId>
  <version>0.1.0</version>
</dependency>
```

```java
OpenCachingClient client = OpenCachingClient.create("https://dcdn.example.net", bearerToken);

// CMI — create a cache bucket
CacheBucket bucket = client.cmi().createBucket(
        CacheBucket.of("live-eu-west", CacheBucket.Type.CACHE).withQuota(500));

// CMI — preposition content into it with a CI trigger
JsonNode command = mapper.readTree("""
        {"cdn-path": ["ucdn.example"],
         "trigger": {"type": "preposition", "urls": ["https://cdn.example/a.ts"]}}
        """);
client.cmi().createTrigger(BucketIdType.NAME, bucket.name(), command);

// FCI — what can this dCDN actually do, and where?
JsonNode advertisement = client.fci().getAdvertisement();

// RRI — ask the dCDN where to send a client
RoutingResponse route = client.rri().routingRequest(
        RoutingRequest.forPath(List.of("ucdn.example", "dcdn.example")).withMaxHops(3));
```

The base URL is supplied by the caller because the specs carry no `servers` block — each dCDN
publishes its own host.

## The parts that are Open-Caching-specific

Most of this client is ordinary HTTP plumbing. Four things are not, and they are where the
spec reading actually mattered:

- **The Content-Type selects the operation.** CMI trigger endpoints are dispatched on the CDNI
  `ptype` media-type parameter, not on the path: creating sends
  `application/cdni;ptype=ci-trigger-command.create.v2` and modifying sends
  `…ci-trigger-command.modify.v2` to a path that is otherwise indistinguishable. The constants
  live in `CdniMediaTypes` and are set per call.
- **3xx is protocol, not failure.** CMI and FCI reads document `304 Not Modified` as a normal
  outcome, and RRI request-driven delegation answers `302`/`307` with a `Location`. No 3xx is
  turned into an exception, and the client never follows a redirect on the caller's behalf —
  `rri().requestDriven(...)` hands back the raw status and `Location` so the caller decides.
- **`501` is an answer, not an outage.** A dCDN that does not support trigger modification
  replies 501. That is permanent, so it is surfaced as
  `OpenCachingException.isNotImplemented()` and never retried. `425 Too Early` (routing state
  not ready) and 500/502/503/504 are retried with exponential backoff.
- **Unknown enum values do not break parsing.** The SVTA revisions add bucket types and
  statuses; an unrecognised value deserialises to `UNRECOGNIZED` rather than throwing, so a
  client built against `COI_v2.1` keeps working against a newer dCDN.

JSON is kebab-case (`cdn-path`, `max-hops`, `trigger-id`) per the specs, handled by a
`KEBAB_CASE` naming strategy; nulls are omitted from request bodies.

## Where the types stop

`CacheBucket`, `RoutingRequest` and `RoutingResponse` are typed records with required-field
validation. Triggers and FCI advertisements are passed and returned as Jackson `JsonNode` on
purpose: the `ci-trigger-command.*` schemas live in the separate Trigger document rather than
in CMI, and an FCI advertisement is an open-ended list of capability objects extended by every
SVTA revision. Inventing records for those would have meant guessing at fields. The media
types and paths — the parts that decide whether the call reaches the right operation at all —
are correct.

## Testing

```bash
mvn test
```

11 tests run against an in-process JDK `HttpServer` stub that records what the client actually
sent — method, path, `Authorization`, `Content-Type`, body — and replays scripted responses.
They cover kebab-case serialisation, enum tolerance, path encoding of bucket names containing
`/` and spaces, the trigger `ptype` Content-Types, 304-as-success, the 425 retry, and 501/409/403
being distinguishable. **All 11 pass.**

No test touches a live dCDN — Open Caching deployments are operator-private and there is no
public sandbox — so this is verified against the published specs and a stub, not against a
production Velocix or other dCDN instance.

## Licence

MIT. The Open Caching specifications are © Streaming Video Technology Alliance, MIT licensed.
