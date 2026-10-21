package com.theclub.common.media;

import com.theclub.common.web.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Against a real SeaweedFS S3 gateway, the store self-hosted deployments use, configured like docker-compose.yml.
 * Runs wherever Docker is available (CI).
 */
@Testcontainers(disabledWithoutDocker = true)
class S3MediaStoreTest {

    static final String ACCESS_KEY = "theclub";
    static final String SECRET_KEY = "test-secret-key-1234";
    static final String S3_CONFIG = """
            {"identities":[
              {"name":"theclub","credentials":[{"accessKey":"%s","secretKey":"%s"}],"actions":["Admin","Read","List","Write"]},
              {"name":"anonymous","actions":["Read:media"]}]}
            """.formatted(ACCESS_KEY, SECRET_KEY);

    @Container
    static final GenericContainer<?> SEAWEED = new GenericContainer<>("chrislusf/seaweedfs:4.47")
            .withCopyToContainer(Transferable.of(S3_CONFIG), "/etc/seaweedfs/s3.json")
            .withCommand("server", "-s3", "-s3.config=/etc/seaweedfs/s3.json", "-dir=/data", "-volume.max=5")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCodeMatching(code -> code < 500));

    static S3Client s3;
    static S3MediaStore store;

    @BeforeAll
    static void setUp() {
        String endpoint = "http://" + SEAWEED.getHost() + ":" + SEAWEED.getMappedPort(8333);
        s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClient(UrlConnectionHttpClient.create())
                // Only the checksums S3 requires: the SDK's default extra checksums are rejected by some S3-compatible
                // stores (SeaweedFS answers "The Content-Md5 you specified is not valid")
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        // No bucket yet: the first upload creates it
        store = new S3MediaStore(s3, new MediaProperties(endpoint, null, "media", ACCESS_KEY, SECRET_KEY,
                "/media", true, DataSize.ofKilobytes(64)));
    }

    @Test
    void storesAnImageUnderARandomNameWithItsDetectedType() {
        StoredImage stored = store.storeImage("profiles", upload("me.png", "application/octet-stream", ImageTypeTest.PNG));

        assertThat(stored.key()).matches("profiles/[0-9a-f-]{36}\\.png");
        assertThat(stored.url()).isEqualTo("/media/" + stored.key());
        HeadObjectResponse head = s3.headObject(h -> h.bucket("media").key(stored.key()));
        // The declared type is ignored: the stored type comes from the bytes
        assertThat(head.contentType()).isEqualTo("image/png");
        assertThat(head.cacheControl()).contains("immutable");
    }

    @Test
    void storedImagesArePubliclyReadableButNotWritable() throws Exception {
        StoredImage stored = store.storeImage("profiles", upload("me.jpg", "image/jpeg", ImageTypeTest.JPEG));
        String base = "http://" + SEAWEED.getHost() + ":" + SEAWEED.getMappedPort(8333) + "/media/";
        HttpClient http = HttpClient.newHttpClient();

        HttpResponse<byte[]> get = http.send(HttpRequest.newBuilder(URI.create(base + stored.key())).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.body()).isEqualTo(ImageTypeTest.JPEG);

        HttpResponse<Void> put = http.send(HttpRequest.newBuilder(URI.create(base + "profiles/forged.jpg"))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(ImageTypeTest.JPEG)).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(403);
    }

    @Test
    void refusesFilesThatAreNotJpegPngOrWebp() {
        byte[] svg = "<svg onload=\"alert(1)\"/>".getBytes();
        assertThatThrownBy(() -> store.storeImage("profiles", upload("x.png", "image/png", svg)))
                .isInstanceOf(ApiException.class).hasMessageContaining("JPEG, PNG or WebP");
    }

    @Test
    void refusesFilesOverTheSizeLimit() {
        byte[] big = Arrays.copyOf(ImageTypeTest.JPEG, 65 * 1024);
        assertThatThrownBy(() -> store.storeImage("profiles", upload("big.jpg", "image/jpeg", big)))
                .isInstanceOf(ApiException.class).hasMessageContaining("at most");
    }

    @Test
    void deletesItsOwnImagesAndIgnoresOtherUrls() {
        StoredImage stored = store.storeImage("clubs/c1", upload("a.jpg", "image/jpeg", ImageTypeTest.JPEG));

        store.deleteByUrl("https://elsewhere.example/a.jpg");
        assertThat(s3.headObject(h -> h.bucket("media").key(stored.key())).contentLength()).isPositive();

        store.deleteByUrl(stored.url());
        assertThatThrownBy(() -> s3.headObject(h -> h.bucket("media").key(stored.key())))
                .isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void keepsPrivateDocumentsAndReadsThemBack() {
        byte[] pdf = "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        StoredDocument stored = store.storePrivate("verification/r1", upload("proof.pdf", "application/octet-stream", pdf));
        assertThat(stored.key()).startsWith("private/verification/r1/").endsWith(".pdf");
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(store.readPrivate(stored.key())).hasValue(pdf);

        StoredDocument photo = store.storePrivate("verification/r1", upload("id.png", "image/png", ImageTypeTest.PNG));
        assertThat(photo.contentType()).isEqualTo("image/png");
        // only keys under private/ are read or deleted through these methods
        assertThat(store.readPrivate("profiles/x.png")).isEmpty();
        assertThatThrownBy(() -> store.storePrivate("verification/r1",
                upload("x.html", "application/pdf", "<html>".getBytes(java.nio.charset.StandardCharsets.US_ASCII))))
                .isInstanceOf(ApiException.class);

        store.deletePrivate(stored.key());
        assertThat(store.readPrivate(stored.key())).isEmpty();
    }

    @Test
    void refusesUnsafeFolders() {
        assertThatThrownBy(() -> store.storeImage("../etc", upload("a.jpg", "image/jpeg", ImageTypeTest.JPEG)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockMultipartFile upload(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }
}
