package com.theclub.common.media;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

import java.util.Optional;

/**
 * Keeps uploads in Google Cloud Storage. The buckets are created up front (Terraform, the console); credentials come
 * from GKE Workload Identity or another application-default source.
 */
public class GcsMediaStore extends BlobMediaStore {

    private final Storage storage;

    public GcsMediaStore(Storage storage, MediaProperties properties) {
        super(properties);
        this.storage = storage;
    }

    @Override
    protected void put(String bucket, String key, String contentType, String cacheControl, byte[] bytes) {
        storage.create(BlobInfo.newBuilder(BlobId.of(bucket, key)).setContentType(contentType).setCacheControl(cacheControl)
                .build(), bytes);
    }

    @Override
    protected Optional<byte[]> get(String bucket, String key) {
        var blob = storage.get(BlobId.of(bucket, key));
        return blob == null ? Optional.empty() : Optional.of(blob.getContent());
    }

    @Override
    protected void remove(String bucket, String key) {
        storage.delete(BlobId.of(bucket, key));
    }
}
