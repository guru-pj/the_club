package com.theclub.common.media;

/**
 * @param url public URL to store with the entity and show in the app
 * @param key object key inside the bucket
 */
public record StoredImage(String url, String key) {
}
