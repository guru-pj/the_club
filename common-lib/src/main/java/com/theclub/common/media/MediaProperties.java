package com.theclub.common.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Where uploaded images are kept: an S3-compatible bucket (MinIO when self-hosted). Disabled until {@code endpoint}
 * and {@code bucket} are set; uploads then answer 503 instead of failing.
 *
 * @param endpoint      S3 API endpoint, e.g. http://minio:9000 or https://s3.eu-central-1.amazonaws.com
 * @param publicBaseUrl where browsers load the objects from; "/media" is proxied to the bucket by the web app's nginx
 * @param pathStyle     bucket in the path (MinIO) instead of the host name (AWS virtual-hosted style)
 * @param maxSize       largest accepted upload
 */
@ConfigurationProperties(prefix = "theclub.media")
public record MediaProperties(String endpoint, String region, String bucket, String accessKey, String secretKey,
                              String publicBaseUrl, Boolean pathStyle, DataSize maxSize) {

    public MediaProperties {
        region = region == null || region.isBlank() ? "us-east-1" : region;
        publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank() ? "/media" : publicBaseUrl.replaceAll("/+$", "");
        pathStyle = pathStyle == null || pathStyle;
        maxSize = maxSize == null ? DataSize.ofMegabytes(5) : maxSize;
    }

    public boolean configured() {
        return endpoint != null && !endpoint.isBlank() && bucket != null && !bucket.isBlank();
    }
}
