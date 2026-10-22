package com.theclub.common.media;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Azure Blob Storage, against Azurite with its well-known development account. */
@Testcontainers(disabledWithoutDocker = true)
class AzureMediaStoreTest extends MediaStoreContract {

    /** Azurite's published development key, not a secret. */
    static final String DEV_KEY = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    @Container
    static final GenericContainer<?> AZURITE = new GenericContainer<>("mcr.microsoft.com/azure-storage/azurite:3.34.0")
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck", "--loose")
            .withExposedPorts(10000)
            .waitingFor(Wait.forListeningPort());

    static MediaStore store;
    static BlobServiceClient service;

    @BeforeAll
    static void setUp() {
        String connection = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=" + DEV_KEY
                + ";BlobEndpoint=http://" + AZURITE.getHost() + ":" + AZURITE.getMappedPort(10000) + "/devstoreaccount1;";
        service = new BlobServiceClientBuilder().connectionString(connection).buildClient();
        // no containers yet: the first upload creates them on an emulator
        store = new MediaAutoConfiguration().mediaStore(properties("azure", null, connection));
    }

    @Override
    MediaStore store() {
        return store;
    }

    @Override
    byte[] object(String container, String key) {
        var blob = service.getBlobContainerClient(container).getBlobClient(key);
        return blob.exists() ? blob.downloadContent().toBytes() : null;
    }
}
