package com.recruitinbox.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Private blob store abstraction. Dev uses the filesystem; prod swaps in the S3
 * adapter via {@code storage.provider=s3} with no call-site changes.
 */
public interface ObjectStorage {

    void put(String key, byte[] bytes, String contentType);

    Optional<byte[]> get(String key);

    void delete(String key);

    /** Short-lived read URL for the owner; may be an app route for the local adapter. */
    URI presignedGetUrl(String key, Duration ttl);
}
