package com.theclub.common.media;

import com.theclub.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What every storage backend must do, run against each one's emulator. */
abstract class MediaStoreContract {

    static final String BUCKET = "media";
    static final String PRIVATE_BUCKET = "media-private";
    static final byte[] PDF = "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    /** A store built by {@link MediaAutoConfiguration} for this backend. */
    abstract MediaStore store();

    /** The object's bytes read straight from the backend, or null when there's none. */
    abstract byte[] object(String bucket, String key);

    static MediaProperties properties(String provider, String endpoint, String connectionString) {
        return new MediaProperties(provider, endpoint, null, BUCKET, PRIVATE_BUCKET, null, null, connectionString,
                "test-project", "https://cdn.example.com/media", null, DataSize.ofKilobytes(64));
    }

    static MockMultipartFile upload(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    @Test
    void storesImagesUnderTheirPublicUrlAndDeletesThem() {
        StoredImage stored = store().storeImage("clubs/c1", upload("logo.png", "application/octet-stream", ImageTypeTest.PNG));

        assertThat(stored.url()).isEqualTo("https://cdn.example.com/media/" + stored.key());
        assertThat(stored.key()).startsWith("clubs/c1/").endsWith(".png");
        assertThat(object(BUCKET, stored.key())).isEqualTo(ImageTypeTest.PNG);

        store().deleteByUrl("https://elsewhere.example/" + stored.key());
        assertThat(object(BUCKET, stored.key())).isNotNull();
        store().deleteByUrl(stored.url());
        assertThat(object(BUCKET, stored.key())).isNull();
    }

    @Test
    void keepsPrivateDocumentsInThePrivateBucket() {
        StoredDocument stored = store().storePrivate("verification/r1", upload("proof.pdf", "application/octet-stream", PDF));

        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(object(PRIVATE_BUCKET, stored.key())).isEqualTo(PDF);
        assertThat(object(BUCKET, stored.key())).isNull();
        assertThat(store().readPrivate(stored.key())).hasValue(PDF);
        assertThat(store().readPrivate("private/verification/r1/missing.pdf")).isEmpty();
        assertThat(store().readPrivate("clubs/c1/x.png")).isEmpty();

        store().deletePrivate(stored.key());
        assertThat(store().readPrivate(stored.key())).isEmpty();
    }

    @Test
    void refusesWrongTypesAndOversizedFiles() {
        assertThatThrownBy(() -> store().storeImage("clubs", upload("x.png", "image/png", "<svg/>".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> store().storeImage("clubs", upload("big.jpg", "image/jpeg", Arrays.copyOf(ImageTypeTest.JPEG, 65 * 1024))))
                .isInstanceOf(ApiException.class).hasMessageContaining("at most");
    }
}
