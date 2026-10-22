package com.theclub.common.media;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;

/** Keeps uploads in an S3-compatible bucket (SeaweedFS, AWS S3, Cloudflare R2, MinIO). */
public class S3MediaStore extends BlobMediaStore {

    private final S3Client s3;

    public S3MediaStore(S3Client s3, MediaProperties properties) {
        super(properties);
        this.s3 = s3;
    }

    @Override
    protected void put(String bucket, String key, String contentType, String cacheControl, byte[] bytes) {
        PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
                .cacheControl(cacheControl).build();
        try {
            s3.putObject(put, RequestBody.fromBytes(bytes));
        } catch (NoSuchBucketException e) {
            // First upload on a fresh self-hosted store; a managed bucket (AWS, R2) normally exists already
            s3.createBucket(b -> b.bucket(bucket));
            s3.putObject(put, RequestBody.fromBytes(bytes));
        }
    }

    @Override
    protected Optional<byte[]> get(String bucket, String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray());
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return Optional.empty();
        }
    }

    @Override
    protected void remove(String bucket, String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
