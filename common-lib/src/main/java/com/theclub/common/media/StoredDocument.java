package com.theclub.common.media;

/**
 * A private document in the bucket.
 *
 * @param key         its object key, always under {@code private/}
 * @param contentType detected from the content, not the name
 */
public record StoredDocument(String key, String contentType, long size) {
}
