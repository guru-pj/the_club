package com.theclub.common.media;

import com.theclub.common.util.UuidGenerator;
import com.theclub.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Keeps images in an S3-compatible bucket; objects are public-read through {@link MediaProperties#publicBaseUrl()}. */
public class S3MediaStore implements MediaStore {

    private static final Logger log = LoggerFactory.getLogger(S3MediaStore.class);
    private static final Pattern SAFE_FOLDER = Pattern.compile("[a-z0-9]+(/[a-z0-9-]+)*");
    /** Names are random, so an object never changes and can be cached for good. */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3;
    private final MediaProperties properties;

    public S3MediaStore(S3Client s3, MediaProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public StoredImage storeImage(String folder, MultipartFile file) {
        if (!SAFE_FOLDER.matcher(folder).matches()) {
            throw new IllegalArgumentException("Unsafe media folder: " + folder);
        }
        byte[] bytes = read(file);
        ImageType type = ImageType.detect(Arrays.copyOf(bytes, Math.min(bytes.length, 12)))
                .orElseThrow(() -> ApiException.badRequest("Upload a JPEG, PNG or WebP image")
                        .withCode("UNSUPPORTED_IMAGE"));
        String key = folder + "/" + UuidGenerator.generate() + "." + type.extension();
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(type.contentType())
                .cacheControl(CACHE_CONTROL)
                .build();
        try {
            try {
                s3.putObject(put, RequestBody.fromBytes(bytes));
            } catch (NoSuchBucketException e) {
                // First upload on a fresh self-hosted store; a managed bucket (AWS, R2) normally exists already
                s3.createBucket(b -> b.bucket(properties.bucket()));
                s3.putObject(put, RequestBody.fromBytes(bytes));
            }
        } catch (SdkException e) {
            log.warn("Storing image {} failed: {}", key, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The image couldn't be saved; try again shortly")
                    .withCode("MEDIA_UNAVAILABLE");
        }
        return new StoredImage(properties.publicBaseUrl() + "/" + key, key);
    }

    @Override
    public void deleteByUrl(String url) {
        String prefix = properties.publicBaseUrl() + "/";
        if (url == null || !url.startsWith(prefix)) {
            return;
        }
        String key = url.substring(prefix.length());
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (SdkException e) {
            // An orphaned object is harmless; the entity no longer points at it
            log.warn("Deleting image {} failed: {}", key, e.getMessage());
        }
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose an image to upload").withCode("EMPTY_UPLOAD");
        }
        if (file.getSize() > properties.maxSize().toBytes()) {
            throw tooLarge();
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > properties.maxSize().toBytes()) {
                throw tooLarge();
            }
            return bytes;
        } catch (IOException e) {
            throw ApiException.badRequest("The upload couldn't be read");
        }
    }

    private ApiException tooLarge() {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE,
                "Images can be at most " + properties.maxSize().toMegabytes() + " MB").withCode("IMAGE_TOO_LARGE");
    }
}
