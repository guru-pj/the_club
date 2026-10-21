package com.theclub.clubservice.dto;

/** Post photos are pasted http(s) links or images uploaded through {@code POST /posts/photos} ("/media/…"). */
public final class PostPhotos {

    public static final String URL_PATTERN = "^(https?://|/(?!/)).*";
    public static final String URL_MESSAGE = "must be an http(s) URL or an uploaded image";

    private PostPhotos() {
    }
}
