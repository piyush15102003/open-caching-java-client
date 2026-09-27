package com.opencaching.client.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

public final class OpenCachingObjectMapper {

    private OpenCachingObjectMapper() {
    }

    /** Open Caching payloads use kebab-case field names (cdn-path, max-hops); unknown fields are ignored so new attributes don't break parsing. */
    public static ObjectMapper create() {
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
    }
}
