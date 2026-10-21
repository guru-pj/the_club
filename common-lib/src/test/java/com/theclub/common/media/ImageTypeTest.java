package com.theclub.common.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Test
    void recognisesAcceptedFormatsByContent() {
        assertThat(ImageType.detect(JPEG)).contains(ImageType.JPEG);
        assertThat(ImageType.detect(PNG)).contains(ImageType.PNG);
        assertThat(ImageType.detect(WEBP)).contains(ImageType.WEBP);
    }

    @Test
    void rejectsSvgGifAndAnythingElse() {
        assertThat(ImageType.detect("<svg xmlns=\"http://www.w3.org/2000/svg\">".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageType.detect("GIF89a......".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageType.detect("%PDF-1.7....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
    }
}
