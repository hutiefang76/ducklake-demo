package com.lanxinai.data.paltform.ducklake.etl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class EtlLiveIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_ETL_LIVE", matches = "true")
    void streamsExcelToSeaweedFsAndPersistsPostgresLedger() throws Exception {
        EtlPlatformProperties properties = propertiesFromEnvironment();
        EtlLedgerRepository ledger = new EtlLedgerRepository(properties);
        EtlArtifactService service = new EtlArtifactService(properties, ledger);
        var file = new MockMultipartFile(
                "file", "live-smoke.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "live-excel-upload-smoke".getBytes(StandardCharsets.UTF_8));

        var artifact = service.upload(file, "codex-live-smoke");
        String objectKey = artifact.uri().substring(("s3://" + properties.getArtifact().getBucket() + "/").length());
        try (S3Client s3 = s3(properties)) {
            try {
                assertThat(s3.headObject(builder -> builder
                        .bucket(properties.getArtifact().getBucket()).key(objectKey)).contentLength())
                        .isEqualTo(file.getSize());
                assertThat(ledger.findArtifact(artifact.artifactId())).contains(artifact);
            } finally {
                try (var connection = DriverManager.getConnection(
                        properties.getLedger().getJdbcUrl(),
                        properties.getLedger().getUsername(),
                        properties.getLedger().getPassword());
                     var statement = connection.prepareStatement(
                             "DELETE FROM " + properties.getLedger().getSchema() + ".etl_artifact WHERE artifact_id=?")) {
                    statement.setString(1, artifact.artifactId());
                    statement.executeUpdate();
                } finally {
                    s3.deleteObject(builder -> builder.bucket(properties.getArtifact().getBucket()).key(objectKey));
                }
            }
        }
    }

    private static EtlPlatformProperties propertiesFromEnvironment() {
        EtlPlatformProperties properties = new EtlPlatformProperties();
        properties.setEnabled(true);
        properties.getArtifact().setEndpoint(required("ETL_ARTIFACT_S3_ENDPOINT"));
        properties.getArtifact().setRegion(required("ETL_ARTIFACT_S3_REGION"));
        properties.getArtifact().setAccessKey(required("ETL_ARTIFACT_S3_ACCESS_KEY"));
        properties.getArtifact().setSecretKey(required("ETL_ARTIFACT_S3_SECRET_KEY"));
        properties.getArtifact().setBucket(required("ETL_ARTIFACT_S3_BUCKET"));
        properties.getArtifact().setPrefix(required("ETL_ARTIFACT_S3_PREFIX"));
        properties.getLedger().setJdbcUrl(required("ETL_LEDGER_JDBC_URL"));
        properties.getLedger().setUsername(required("ETL_LEDGER_USERNAME"));
        properties.getLedger().setPassword(required("ETL_LEDGER_PASSWORD"));
        properties.getLedger().setSchema(required("ETL_LEDGER_SCHEMA"));
        return properties;
    }

    private static S3Client s3(EtlPlatformProperties properties) {
        var artifact = properties.getArtifact();
        return S3Client.builder()
                .endpointOverride(URI.create(artifact.getEndpoint()))
                .region(Region.of(artifact.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(artifact.getAccessKey(), artifact.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
