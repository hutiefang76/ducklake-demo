package com.lanxinai.data.paltform.ducklake.etl;

import com.lanxinai.data.paltform.ducklake.etl.EtlLedgerRepository.ArtifactRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class EtlArtifactService {

    private static final Set<String> EXTENSIONS = Set.of(".xlsx", ".xls", ".parquet", ".csv");
    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);

    private final EtlPlatformProperties properties;
    private final EtlLedgerRepository ledger;
    private volatile S3Client client;

    public EtlArtifactService(EtlPlatformProperties properties, EtlLedgerRepository ledger) {
        this.properties = properties;
        this.ledger = ledger;
    }

    public ArtifactRecord upload(MultipartFile file, String requestedBy) {
        properties.requireAvailable();
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("file is required");
        if (file.getSize() > properties.getArtifact().getMaxBytes()) throw new IllegalArgumentException("file exceeds ETL_ARTIFACT_MAX_BYTES");
        String originalName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String extension = extension(originalName);
        if (!EXTENSIONS.contains(extension)) throw new IllegalArgumentException("Only Excel, Parquet and CSV artifacts are allowed");
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? contentType(extension) : file.getContentType();
        String artifactId = "art_" + UUID.randomUUID().toString().replace("-", "");
        String key = prefix() + "/artifacts/" + DATE_PREFIX.format(Instant.now()) + "/" + artifactId + extension;
        MessageDigest digest = sha256();
        try (DigestInputStream stream = new DigestInputStream(file.getInputStream(), digest)) {
            s3().putObject(
                    PutObjectRequest.builder().bucket(bucket()).key(key).contentType(contentType).build(),
                    RequestBody.fromInputStream(stream, file.getSize()));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to stream ETL artifact to object storage", exception);
        }
        ArtifactRecord record = new ArtifactRecord(
                artifactId, s3Uri(key), originalName, contentType, file.getSize(),
                HexFormat.of().formatHex(digest.digest()), requiredIdentity(requestedBy),
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        try {
            ledger.saveArtifact(record);
            return record;
        } catch (RuntimeException failure) {
            try {
                s3().deleteObject(builder -> builder.bucket(bucket()).key(key));
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public StoredObject putJson(String category, String id, byte[] bytes) {
        properties.requireAvailable();
        String key = prefix() + "/" + category + "/" + DATE_PREFIX.format(Instant.now()) + "/" + id + ".json";
        s3().putObject(
                PutObjectRequest.builder().bucket(bucket()).key(key).contentType("application/json").build(),
                RequestBody.fromBytes(bytes));
        return new StoredObject(s3Uri(key), HexFormat.of().formatHex(sha256().digest(bytes)));
    }

    public ArtifactRecord requireArtifact(String artifactId) {
        return ledger.findArtifact(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown artifactId: " + artifactId));
    }

    private S3Client s3() {
        S3Client current = client;
        if (current != null) return current;
        synchronized (this) {
            if (client == null) {
                var config = properties.getArtifact();
                client = S3Client.builder()
                        .endpointOverride(URI.create(config.getEndpoint()))
                        .region(Region.of(config.getRegion()))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .build();
            }
            return client;
        }
    }

    private String bucket() { return properties.getArtifact().getBucket(); }
    private String prefix() { return properties.getArtifact().getPrefix().replaceAll("^/+|/+$", ""); }
    private String s3Uri(String key) { return "s3://" + bucket() + "/" + key; }
    private static String requiredIdentity(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("requestedBy is required");
        return value.trim();
    }
    private static String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index).toLowerCase(Locale.ROOT);
    }
    private static String contentType(String extension) {
        return switch (extension) {
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".xls" -> "application/vnd.ms-excel";
            case ".parquet" -> "application/x-parquet";
            default -> "text/csv";
        };
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record StoredObject(String uri, String sha256) {}
}
