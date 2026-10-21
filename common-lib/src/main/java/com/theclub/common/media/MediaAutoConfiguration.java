package com.theclub.common.media;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/** A {@link MediaStore} for services that depend on the S3 SDK; without a configured bucket uploads answer 503. */
@AutoConfiguration
@ConditionalOnClass(S3Client.class)
@EnableConfigurationProperties(MediaProperties.class)
public class MediaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MediaStore mediaStore(MediaProperties properties) {
        if (!properties.configured()) {
            return new DisabledMediaStore();
        }
        S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build())
                .httpClient(UrlConnectionHttpClient.create())
                // Only the checksums S3 requires: the SDK's default extra checksums are rejected by some S3-compatible
                // stores (SeaweedFS answers "The Content-Md5 you specified is not valid")
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        return new S3MediaStore(s3, properties);
    }
}
