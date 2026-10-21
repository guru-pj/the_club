package com.theclub.common.media;

import java.util.Optional;

/** Private documents (e.g. proof of club ownership): the accepted image formats, or PDF. */
public final class DocumentType {

    public static final String PDF = "application/pdf";

    private DocumentType() {
    }

    /** The content type, recognised by the first bytes. */
    public static Optional<String> detect(byte[] head) {
        Optional<ImageType> image = ImageType.detect(head);
        if (image.isPresent()) {
            return image.map(ImageType::contentType);
        }
        if (head.length >= 5 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-') {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    public static String extension(String contentType) {
        return PDF.equals(contentType) ? "pdf" : java.util.Arrays.stream(ImageType.values())
                .filter(t -> t.contentType().equals(contentType)).findFirst().map(ImageType::extension).orElse("bin");
    }
}
