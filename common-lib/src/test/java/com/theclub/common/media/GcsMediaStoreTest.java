package com.theclub.common.media;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Google Cloud Storage, against fake-gcs-server (the buckets are created up front, as on GCP). */
@Testcontainers(disabledWithoutDocker = true)
class GcsMediaStoreTest extends MediaStoreContract {

    @Container
    static final GenericContainer<?> GCS = new GenericContainer<>("fsouza/fake-gcs-server:1.52.2")
            .withCommand("-scheme", "http", "-port", "4443")
            .withExposedPorts(4443)
            .waitingFor(Wait.forHttp("/storage/v1/b").forPort(4443));

    static MediaStore store;
    static Storage storage;

    @BeforeAll
    static void setUp() {
        String endpoint = "http://" + GCS.getHost() + ":" + GCS.getMappedPort(4443);
        storage = StorageOptions.newBuilder().setHost(endpoint).setProjectId("test-project")
                .setCredentials(NoCredentials.getInstance()).build().getService();
        storage.create(BucketInfo.of(BUCKET));
        storage.create(BucketInfo.of(PRIVATE_BUCKET));
        store = new MediaAutoConfiguration().mediaStore(properties("gcs", endpoint, null));
    }

    @Override
    MediaStore store() {
        return store;
    }

    @Override
    byte[] object(String bucket, String key) {
        var blob = storage.get(BlobId.of(bucket, key));
        return blob == null ? null : blob.getContent();
    }
}
