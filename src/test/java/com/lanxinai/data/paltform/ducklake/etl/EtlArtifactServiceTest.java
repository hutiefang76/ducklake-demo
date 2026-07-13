package com.lanxinai.data.paltform.ducklake.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class EtlArtifactServiceTest {

    private EtlLedgerRepository ledger;
    private S3Client s3;
    private EtlArtifactService service;

    @BeforeEach
    void setUp() {
        EtlPlatformProperties properties = new EtlPlatformProperties();
        properties.setEnabled(true);
        properties.getArtifact().setEndpoint("http://127.0.0.1:8333");
        properties.getArtifact().setAccessKey("test-access-key");
        properties.getArtifact().setSecretKey("test-secret-key");
        properties.getArtifact().setBucket("dp-springboot-files");
        properties.getArtifact().setPrefix("data-platform-dev/etl-platform");
        properties.getArtifact().setMaxBytes(1024);
        properties.getLedger().setJdbcUrl("jdbc:postgresql://127.0.0.1/test");
        properties.getLedger().setUsername("test");
        properties.getLedger().setPassword("test");
        ledger = mock(EtlLedgerRepository.class);
        s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            RequestBody body = invocation.getArgument(1);
            try (var input = body.contentStreamProvider().newStream()) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return PutObjectResponse.builder().eTag("test-etag").build();
        });
        service = new EtlArtifactService(properties, ledger);
        ReflectionTestUtils.setField(service, "client", s3);
    }

    @Test
    void streamsFileToConfiguredBucketAndPersistsDigest() throws Exception {
        byte[] bytes = "excel-upload-stream".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "material.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        var result = service.upload(file, "doctor");

        assertThat(result.uri()).startsWith(
                "s3://dp-springboot-files/data-platform-dev/etl-platform/artifacts/");
        assertThat(result.uri()).endsWith(".xlsx");
        assertThat(result.sha256()).isEqualTo(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(result.size()).isEqualTo(bytes.length);
        verify(ledger).saveArtifact(result);
    }

    @Test
    void rejectsUnsupportedOrOversizedFilesBeforeS3Call() {
        var unsupported = new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[]{1});
        var oversized = new MockMultipartFile("file", "large.csv", "text/csv", new byte[1025]);

        assertThatThrownBy(() -> service.upload(unsupported, "doctor"))
                .hasMessageContaining("Only Excel, Parquet and CSV");
        assertThatThrownBy(() -> service.upload(oversized, "doctor"))
                .hasMessageContaining("ETL_ARTIFACT_MAX_BYTES");
        org.mockito.Mockito.verifyNoInteractions(s3);
    }

    @Test
    void deletesUploadedObjectWhenLedgerPersistenceFails() {
        doThrow(new IllegalStateException("ledger unavailable"))
                .when(ledger).saveArtifact(any(EtlLedgerRepository.ArtifactRecord.class));
        var file = new MockMultipartFile("file", "material.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(file, "doctor"))
                .hasMessageContaining("ledger unavailable");

        verify(s3).deleteObject(org.mockito.ArgumentMatchers
                .<Consumer<DeleteObjectRequest.Builder>>any());
    }
}
