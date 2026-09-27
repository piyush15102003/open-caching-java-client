package com.opencaching.client;

/**
 * CDNI media types used by the Open Caching interfaces.
 * <p>
 * These are not decoration: the CMI trigger endpoints select their operation by the
 * {@code ptype} parameter on the Content-Type, so sending plain {@code application/json}
 * to {@code POST .../triggers} is rejected. Values are taken verbatim from the SVTA
 * OpenAPI documents in {@code streaming-video-technology-alliance/OC-API}.
 */
public final class CdniMediaTypes {

    private CdniMediaTypes() {
    }

    /** Body type for creating a CI trigger (CMI {@code POST .../V2_14/triggers}). */
    public static final String CI_TRIGGER_COMMAND_CREATE_V2 = "application/cdni;ptype=ci-trigger-command.create.v2";

    /** Body type for modifying an existing CI trigger (CMI {@code POST .../triggers/{triggerId}}). */
    public static final String CI_TRIGGER_COMMAND_MODIFY_V2 = "application/cdni;ptype=ci-trigger-command.modify.v2";

    /** Representation of a CI trigger resource. */
    public static final String CI_TRIGGER_V2 = "application/cdni;ptype=ci-trigger.v2";

    /** Body type for an RRI redirection request (RRI {@code POST /OC/RRI/routing_request}). */
    public static final String REDIRECTION_REQUEST = "application/cdni;ptype=redirection-request";

    /** Generic CDNI type used by FCI subscription bodies. */
    public static final String CDNI = "application/cdni";

    public static final String JSON = "application/json";
}
