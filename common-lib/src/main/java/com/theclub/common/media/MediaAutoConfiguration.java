package com.theclub.common.media;

import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * The {@link MediaStore} for services that store uploads (they depend on the storage SDKs): S3-compatible storage,
 * Google Cloud Storage or Azure Blob Storage, picked by {@code theclub.media.provider}. Without a configured bucket
 * uploads answer 503.
 */
@AutoConfiguration
@ConditionalOnClass(S3Client.class)
@EnableConfigurationProperties(MediaProperties.class)
public class MediaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MediaAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MediaStore mediaStore(MediaProperties properties) {
        if (!properties.configured()) {
            return new DisabledMediaStore();
        }
        MediaStore store = switch (properties.provider()) {
            case MediaProperties.GCS -> Gcs.store(properties);
            case MediaProperties.AZURE -> Azure.store(properties);
            default -> s3(properties);
        };
        log.info("Uploads go to {} bucket {} (private documents: {}), served from {}", properties.provider(),
                properties.bucket(), properties.privateBucketOrDefault(), properties.publicBaseUrl());
        return store;
    }

    private static MediaStore s3(MediaProperties properties) {
        // Static keys for self-hosted stores; otherwise the cloud's own credentials (EKS IRSA or Pod Identity, an
        // instance role, AWS_* environment variables)
        AwsCredentialsProvider credentials = properties.usesStaticS3Keys()
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()))
                : DefaultCredentialsProvider.builder().build();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build())
                .httpClient(UrlConnectionHttpClient.create())
                // Only the checksums S3 requires: the SDK's default extra checksums are rejected by some S3-compatible
                // stores (SeaweedFS answers "The Content-Md5 you specified is not valid")
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (!MediaProperties.isBlank(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return new S3MediaStore(builder.build(), properties);
    }

    /** Separate classes, so the Google and Azure SDK types are only loaded when that provider is used. */
    static final class Gcs {

        static MediaStore store(MediaProperties properties) {
            StorageOptions.Builder options = StorageOptions.newBuilder();
            if (!MediaProperties.isBlank(properties.gcsProjectId())) {
                options.setProjectId(properties.gcsProjectId());
            }
            if (!MediaProperties.isBlank(properties.endpoint())) {
                // an emulator (fake-gcs-server); real GCS uses application default credentials (Workload Identity)
                options.setHost(properties.endpoint()).setCredentials(NoCredentials.getInstance());
            }
            Storage storage = options.build().getService();
            return new GcsMediaStore(storage, properties);
        }
    }

    static final class Azure {

        static MediaStore store(MediaProperties properties) {
            BlobServiceClientBuilder builder = new BlobServiceClientBuilder().httpClient(new JdkHttpClientBuilder().build());
            if (!MediaProperties.isBlank(properties.azureConnectionString())) {
                builder.connectionString(properties.azureConnectionString());
            } else {
                // AKS workload identity, a managed identity, or AZURE_* environment variables
                builder.endpoint(properties.endpoint()).credential(new DefaultAzureCredentialBuilder().build());
            }
            BlobServiceClient service = builder.buildClient();
            return new AzureMediaStore(service, properties);
        }
    }
}
