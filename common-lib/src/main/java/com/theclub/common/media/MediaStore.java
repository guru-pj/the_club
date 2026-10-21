package com.theclub.common.media;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/** Stores uploaded images and removes them again. */
public interface MediaStore {

    /**
     * Validates the upload (accepted format by content, size limit) and stores it under {@code folder}
     * with a random name.
     *
     * @param folder e.g. "profiles", "clubs/{clubId}", "events/{eventId}"
     */
    StoredImage storeImage(String folder, MultipartFile file);

    /** Removes an image this store created earlier; URLs it didn't create (e.g. pasted links) are ignored. */
    void deleteByUrl(String url);

    /**
     * Stores a private document (JPEG, PNG, WebP or PDF, recognised by content) under {@code private/{folder}}.
     * Private objects are never served by the web app's /media proxy; services hand them out to authorised callers.
     */
    StoredDocument storePrivate(String folder, MultipartFile file);

    /** The bytes of a private document, or empty when there is none under {@code key}. */
    Optional<byte[]> readPrivate(String key);

    /** Removes a private document; unknown keys are ignored. */
    void deletePrivate(String key);
}
