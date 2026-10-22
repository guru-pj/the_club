package com.theclub.common.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.Locale;

/**
 * Where uploads are kept: an S3-compatible bucket (SeaweedFS when self-hosted, AWS S3, Cloudflare R2, …), a Google
 * Cloud Storage bucket or an Azure Blob Storage container. Disabled until {@code bucket} is set; uploads then answer
 * 503 instead of failing.
 *
 * @param provider               s3 (default), gcs or azure
 * @param endpoint               s3: the API endpoint, e.g. http://seaweedfs:8333; empty for AWS itself (from
 *                               {@code region}). gcs: only for an emulator. azure: the account URL,
 *                               https://{account}.blob.core.windows.net
 * @param bucket                 the bucket (s3, gcs) or container (azure) for images, which browsers read
 * @param privateBucket          where private documents go (club application proofs). Empty: the same bucket under
 *                               {@code private/}. Set it on clouds whose public access can't exclude one prefix
 * @param accessKey              s3: the access key; empty means the cloud's own credentials (EKS IRSA / Pod Identity,
 *                               an instance role)
 * @param secretKey              s3: its secret
 * @param azureConnectionString  azure: a connection string (account key, Azurite); empty means workload / managed
 *                               identity
 * @param gcsProjectId           gcs: the project; empty means the one the credentials belong to
 * @param publicBaseUrl          where browsers load images from: "/media" (proxied by the web app's nginx) or the
 *                               bucket's public URL or a CDN
 * @param pathStyle              s3: bucket in the path (SeaweedFS, MinIO) instead of the host name (AWS)
 * @param maxSize                largest accepted upload
 */
@ConfigurationProperties(prefix = "theclub.media")
public record MediaProperties(String provider, String endpoint, String region, String bucket, String privateBucket,
                              String accessKey, String secretKey, String azureConnectionString, String gcsProjectId,
                              String publicBaseUrl, Boolean pathStyle, DataSize maxSize) {

    public static final String S3 = "s3";
    public static final String GCS = "gcs";
    public static final String AZURE = "azure";

    public MediaProperties {
        provider = provider == null || provider.isBlank() ? S3 : provider.trim().toLowerCase(Locale.ROOT);
        if (!provider.equals(S3) && !provider.equals(GCS) && !provider.equals(AZURE)) {
            throw new IllegalArgumentException("theclub.media.provider must be s3, gcs or azure, got " + provider);
        }
        region = region == null || region.isBlank() ? "us-east-1" : region;
        publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank() ? "/media" : publicBaseUrl.replaceAll("/+$", "");
        pathStyle = pathStyle == null || pathStyle;
        maxSize = maxSize == null ? DataSize.ofMegabytes(5) : maxSize;
    }

    public boolean configured() {
        if (isBlank(bucket)) {
            return false;
        }
        return !provider.equals(AZURE) || !isBlank(endpoint) || !isBlank(azureConnectionString);
    }

    /** The bucket or container private documents go to. */
    public String privateBucketOrDefault() {
        return isBlank(privateBucket) ? bucket : privateBucket;
    }

    public boolean usesStaticS3Keys() {
        return !isBlank(accessKey);
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
