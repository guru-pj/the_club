package com.theclub.common.media;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import java.util.Optional;

/**
 * Keeps uploads in Azure Blob Storage, one container for images and optionally one for private documents (created up
 * front). Credentials come from AKS workload identity or a managed identity, or a connection string.
 */
public class AzureMediaStore extends BlobMediaStore {

    private final BlobServiceClient service;

    public AzureMediaStore(BlobServiceClient service, MediaProperties properties) {
        super(properties);
        this.service = service;
    }

    @Override
    protected void put(String container, String key, String contentType, String cacheControl, byte[] bytes) {
        var blob = service.getBlobContainerClient(container).getBlobClient(key);
        var options = new BlobParallelUploadOptions(BinaryData.fromBytes(bytes))
                .setHeaders(new BlobHttpHeaders().setContentType(contentType).setCacheControl(cacheControl));
        try {
            blob.uploadWithResponse(options, null, null);
        } catch (BlobStorageException e) {
            if (e.getErrorCode() != BlobErrorCode.CONTAINER_NOT_FOUND) {
                throw e;
            }
            // a fresh emulator (Azurite); real containers are created up front
            service.getBlobContainerClient(container).createIfNotExists();
            blob.uploadWithResponse(options, null, null);
        }
    }

    @Override
    protected Optional<byte[]> get(String container, String key) {
        try {
            return Optional.of(service.getBlobContainerClient(container).getBlobClient(key).downloadContent().toBytes());
        } catch (BlobStorageException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    protected void remove(String container, String key) {
        service.getBlobContainerClient(container).getBlobClient(key).deleteIfExists();
    }
}
