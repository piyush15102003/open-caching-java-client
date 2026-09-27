package com.opencaching.client.model;

/**
 * The {@code bucketIdType} path segment on the CMI trigger routes — whether the bucket in the
 * path is addressed by its name or by its id.
 */
public enum BucketIdType {
    NAME("bucketName"),
    ID("bucketId");

    private final String segment;

    BucketIdType(String segment) {
        this.segment = segment;
    }

    public String segment() {
        return segment;
    }
}
