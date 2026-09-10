package com.recruitinbox.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dev/local blob store under {@code storage.local.base-dir}. Read URLs point at
 * the app's owner-checked {@code GET /api/v1/uploads/{id}} route rather than a
 * real presign.
 */
@Configuration
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "local", matchIfMissing = true)
class LocalFilesystemObjectStorageConfig {

    @Bean
    ObjectStorage localObjectStorage(StorageProperties props) {
        return new LocalFilesystemObjectStorage(Path.of(props.local().baseDir()), props.publicBaseUrl());
    }

    static final class LocalFilesystemObjectStorage implements ObjectStorage {

        private final Path baseDir;
        private final String publicBaseUrl;

        LocalFilesystemObjectStorage(Path baseDir, String publicBaseUrl) {
            this.baseDir = baseDir;
            this.publicBaseUrl = publicBaseUrl;
            try {
                Files.createDirectories(baseDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private Path resolve(String key) {
            Path p = baseDir.resolve(key).normalize();
            if (!p.startsWith(baseDir)) {
                throw new IllegalArgumentException("key escapes the storage root");
            }
            return p;
        }

        @Override
        public void put(String key, byte[] bytes, String contentType) {
            try {
                Path p = resolve(key);
                Files.createDirectories(p.getParent());
                Files.write(p, bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Optional<byte[]> get(String key) {
            try {
                Path p = resolve(key);
                return Files.exists(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void delete(String key) {
            try {
                Files.deleteIfExists(resolve(key));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public URI presignedGetUrl(String key, Duration ttl) {
            return URI.create(publicBaseUrl + "/api/v1/uploads/by-key/" + key);
        }
    }
}
