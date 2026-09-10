package com.recruitinbox.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Real S3-compatible adapter. Activated by {@code storage.provider=s3}; inert
 * (never constructed) with the default local provider. Credentials come from the
 * standard AWS provider chain (env / profile / IAM role).
 */
@Configuration
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "s3")
class S3ObjectStorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties props) {
        var builder = S3Client.builder().region(Region.of(props.s3().region()));
        if (props.s3().endpoint() != null && !props.s3().endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.s3().endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties props) {
        var builder = S3Presigner.builder().region(Region.of(props.s3().region()));
        if (props.s3().endpoint() != null && !props.s3().endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.s3().endpoint()));
        }
        return builder.build();
    }

    @Bean
    ObjectStorage s3ObjectStorage(S3Client client, S3Presigner presigner, StorageProperties props) {
        return new S3ObjectStorage(client, presigner, props.s3().bucket());
    }

    static final class S3ObjectStorage implements ObjectStorage {

        private final S3Client client;
        private final S3Presigner presigner;
        private final String bucket;

        S3ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
            this.client = client;
            this.presigner = presigner;
            this.bucket = bucket;
        }

        @Override
        public void put(String key, byte[] bytes, String contentType) {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes));
        }

        @Override
        public Optional<byte[]> get(String key) {
            try {
                return Optional.of(client.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray());
            } catch (NoSuchKeyException e) {
                return Optional.empty();
            }
        }

        @Override
        public void delete(String key) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        }

        @Override
        public URI presignedGetUrl(String key, Duration ttl) {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build()).url().toString().transform(URI::create);
        }
    }
}
