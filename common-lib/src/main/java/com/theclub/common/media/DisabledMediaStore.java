package com.theclub.common.media;

import com.theclub.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/** Used when no bucket is configured: uploads are refused with a clear message. */
public class DisabledMediaStore implements MediaStore {

    @Override
    public StoredImage storeImage(String folder, MultipartFile file) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Image uploads aren't configured on this server")
                .withCode("MEDIA_NOT_CONFIGURED");
    }

    @Override
    public void deleteByUrl(String url) {
        // nothing was stored
    }

    @Override
    public StoredDocument storePrivate(String folder, MultipartFile file) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "File uploads aren't configured on this server")
                .withCode("MEDIA_NOT_CONFIGURED");
    }

    @Override
    public Optional<byte[]> readPrivate(String key) {
        return Optional.empty();
    }

    @Override
    public void deletePrivate(String key) {
        // nothing was stored
    }
}
