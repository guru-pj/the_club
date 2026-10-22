package com.theclub.common.media;

import com.theclub.common.util.UuidGenerator;
import com.theclub.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The part of a {@link MediaStore} that doesn't depend on where files go: checking uploads by content and size,
 * naming them, and keeping private documents apart. Subclasses write, read and delete objects in a bucket.
 */
public abstract class BlobMediaStore implements MediaStore {

    private static final Logger log = LoggerFactory.getLogger(BlobMediaStore.class);
    private static final Pattern SAFE_FOLDER = Pattern.compile("[a-z0-9]+(/[a-z0-9-]+)*");
    /** Names are random, so an object never changes and can be cached for good. */
    static final String PUBLIC_CACHE = "public, max-age=31536000, immutable";
    static final String PRIVATE_CACHE = "private, no-store";
    /** Never served by the web app's /media proxy (nginx refuses /media/private/). */
    static final String PRIVATE_PREFIX = "private/";

    protected final MediaProperties properties;

    protected BlobMediaStore(MediaProperties properties) {
        this.properties = properties;
    }

    /** Writes an object, creating the bucket on a fresh self-hosted store if needed. */
    protected abstract void put(String bucket, String key, String contentType, String cacheControl, byte[] bytes);

    /** The object's bytes, or empty when it doesn't exist. */
    protected abstract Optional<byte[]> get(String bucket, String key);

    /** Removes the object; a missing one is not an error. */
    protected abstract void remove(String bucket, String key);

    @Override
    public StoredImage storeImage(String folder, MultipartFile file) {
        requireSafe(folder);
        byte[] bytes = read(file);
        ImageType type = ImageType.detect(head(bytes))
                .orElseThrow(() -> ApiException.badRequest("Upload a JPEG, PNG or WebP image").withCode("UNSUPPORTED_IMAGE"));
        String key = folder + "/" + UuidGenerator.generate() + "." + type.extension();
        write(properties.bucket(), key, type.contentType(), PUBLIC_CACHE, bytes, "image");
        return new StoredImage(properties.publicBaseUrl() + "/" + key, key);
    }

    @Override
    public void deleteByUrl(String url) {
        String prefix = properties.publicBaseUrl() + "/";
        if (url == null || !url.startsWith(prefix)) {
            return;
        }
        delete(properties.bucket(), url.substring(prefix.length()));
    }

    @Override
    public StoredDocument storePrivate(String folder, MultipartFile file) {
        requireSafe(folder);
        byte[] bytes = read(file);
        String type = DocumentType.detect(head(bytes))
                .orElseThrow(() -> ApiException.badRequest("Upload a PDF, JPEG, PNG or WebP file").withCode("UNSUPPORTED_DOCUMENT"));
        String key = PRIVATE_PREFIX + folder + "/" + UuidGenerator.generate() + "." + DocumentType.extension(type);
        write(properties.privateBucketOrDefault(), key, type, PRIVATE_CACHE, bytes, "document");
        return new StoredDocument(key, type, bytes.length);
    }

    @Override
    public Optional<byte[]> readPrivate(String key) {
        if (key == null || !key.startsWith(PRIVATE_PREFIX)) {
            return Optional.empty();
        }
        try {
            return get(properties.privateBucketOrDefault(), key);
        } catch (RuntimeException e) {
            log.warn("Reading document {} failed: {}", key, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The file couldn't be loaded; try again shortly")
                    .withCode("MEDIA_UNAVAILABLE");
        }
    }

    @Override
    public void deletePrivate(String key) {
        if (key != null && key.startsWith(PRIVATE_PREFIX)) {
            delete(properties.privateBucketOrDefault(), key);
        }
    }

    private void write(String bucket, String key, String contentType, String cacheControl, byte[] bytes, String what) {
        try {
            put(bucket, key, contentType, cacheControl, bytes);
        } catch (RuntimeException e) {
            log.warn("Storing {} {} failed: {}", what, key, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The " + what + " couldn't be saved; try again shortly")
                    .withCode("MEDIA_UNAVAILABLE");
        }
    }

    private void delete(String bucket, String key) {
        try {
            remove(bucket, key);
        } catch (RuntimeException e) {
            // An orphaned object is harmless; the entity no longer points at it
            log.warn("Deleting {} failed: {}", key, e.getMessage());
        }
    }

    private static void requireSafe(String folder) {
        if (!SAFE_FOLDER.matcher(folder).matches()) {
            throw new IllegalArgumentException("Unsafe media folder: " + folder);
        }
    }

    private static byte[] head(byte[] bytes) {
        return Arrays.copyOf(bytes, Math.min(bytes.length, 12));
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a file to upload").withCode("EMPTY_UPLOAD");
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
                "Uploads can be at most " + properties.maxSize().toMegabytes() + " MB").withCode("IMAGE_TOO_LARGE");
    }
}
