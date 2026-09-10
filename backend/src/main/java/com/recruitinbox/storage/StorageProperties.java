package com.recruitinbox.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        String provider,
        Local local,
        S3 s3,
        String publicBaseUrl) {

    public StorageProperties {
        provider = provider == null || provider.isBlank() ? "local" : provider;
        local = local == null ? new Local(null) : local;
        s3 = s3 == null ? new S3(null, null, null) : s3;
        publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "http://localhost:8080" : publicBaseUrl;
    }

    public record Local(String baseDir) {
        public Local {
            baseDir = baseDir == null || baseDir.isBlank()
                    ? System.getProperty("java.io.tmpdir") + "/recruit-inbox-assets" : baseDir;
        }
    }

    public record S3(String bucket, String region, String endpoint) {
    }
}
