package com.theclub.common.media;

import org.springframework.web.multipart.MultipartFile;

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
}
