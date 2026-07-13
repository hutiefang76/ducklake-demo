package com.lanxinai.data.paltform.ducklake.etl;

import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Repository
public class EtlLedgerRepository {

    private final EtlPlatformProperties properties;
    private final AtomicBoolean initialized = new AtomicBoolean();

    public EtlLedgerRepository(EtlPlatformProperties properties) {
        this.properties = properties;
    }

    public void saveArtifact(ArtifactRecord artifact) {
        initialize();
        String sql = "INSERT INTO " + schema() + ".etl_artifact "
                + "(artifact_id, object_uri, original_name, content_type, size_bytes, sha256, requested_by, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, artifact.artifactId());
            statement.setString(2, artifact.uri());
            statement.setString(3, artifact.originalName());
            statement.setString(4, artifact.contentType());
            statement.setLong(5, artifact.size());
            statement.setString(6, artifact.sha256());
            statement.setString(7, artifact.requestedBy());
            statement.setObject(8, OffsetDateTime.ofInstant(artifact.createdAt(), ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to persist ETL artifact metadata", exception);
        }
    }

    public Optional<ArtifactRecord> findArtifact(String artifactId) {
        initialize();
        String sql = "SELECT artifact_id, object_uri, original_name, content_type, size_bytes, sha256, requested_by, created_at "
                + "FROM " + schema() + ".etl_artifact WHERE artifact_id=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, artifactId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new ArtifactRecord(
                        result.getString(1), result.getString(2), result.getString(3), result.getString(4),
                        result.getLong(5), result.getString(6), result.getString(7),
                        result.getObject(8, java.time.OffsetDateTime.class).toInstant()));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read ETL artifact metadata", exception);
        }
    }

    public void createRun(String runId, String projectId, String workflowId, String manifestUri,
                          String manifestSha256, String requestedBy) {
        initialize();
        String sql = "INSERT INTO " + schema() + ".etl_run "
                + "(run_id, project_id, workflow_id, manifest_uri, manifest_sha256, requested_by, state, created_at) "
                + "VALUES (?,?,?,?,?,?, 'SUBMITTING', current_timestamp)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            statement.setString(2, projectId);
            statement.setString(3, workflowId);
            statement.setString(4, manifestUri);
            statement.setString(5, manifestSha256);
            statement.setString(6, requestedBy);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create ETL run ledger", exception);
        }
    }

    public void attachWorkflowInstance(String runId, long workflowInstanceId) {
        initialize();
        String sql = "UPDATE " + schema() + ".etl_run "
                + "SET workflow_instance_id=?, state='SUBMITTED', submitted_at=COALESCE(submitted_at, current_timestamp) "
                + "WHERE run_id=? AND (workflow_instance_id IS NULL OR workflow_instance_id=?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workflowInstanceId);
            statement.setString(2, runId);
            statement.setLong(3, workflowInstanceId);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("ETL run ledger row is missing");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update ETL run ledger", exception);
        }
    }

    public Optional<RunRecord> findRunForManifest(
            String runId,
            String projectId,
            String workflowId,
            String manifestUri,
            String manifestSha256) {
        initialize();
        String sql = "SELECT run_id, project_id, workflow_id, workflow_instance_id, manifest_uri, manifest_sha256, requested_by, state "
                + "FROM " + schema() + ".etl_run WHERE project_id=? AND workflow_id=? "
                + "AND manifest_uri=? AND manifest_sha256=?"
                + (runId == null || runId.isBlank() ? "" : " AND run_id=?");
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectId);
            statement.setString(2, workflowId);
            statement.setString(3, manifestUri);
            statement.setString(4, manifestSha256);
            if (runId != null && !runId.isBlank()) statement.setString(5, runId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                RunRecord record = runRecord(result);
                if (result.next()) {
                    throw new IllegalStateException("Multiple ETL runs match the same manifest");
                }
                return Optional.of(record);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to authorize ETL run manifest", exception);
        }
    }

    public Optional<RunRecord> findRunByWorkflowInstance(String projectId, long workflowInstanceId) {
        initialize();
        String sql = "SELECT run_id, project_id, workflow_id, workflow_instance_id, manifest_uri, manifest_sha256, requested_by, state "
                + "FROM " + schema() + ".etl_run WHERE project_id=? AND workflow_instance_id=?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectId);
            statement.setLong(2, workflowInstanceId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(runRecord(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to authorize workflow instance", exception);
        }
    }

    public void markRunFailed(String runId, RuntimeException failure) {
        initialize();
        String sql = "UPDATE " + schema() + ".etl_run SET state='FAILED', error_message=? WHERE run_id=?";
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (message.length() > 2000) message = message.substring(0, 2000);
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, message);
            statement.setString(2, runId);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("ETL run ledger row is missing");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to mark ETL run as failed", exception);
        }
    }

    private void initialize() {
        properties.requireAvailable();
        if (initialized.get()) return;
        synchronized (initialized) {
            if (initialized.get()) return;
            try (Connection connection = connection(); var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema());
                statement.execute("CREATE TABLE IF NOT EXISTS " + schema() + ".etl_artifact ("
                        + "artifact_id VARCHAR(64) PRIMARY KEY, object_uri TEXT NOT NULL, original_name TEXT NOT NULL,"
                        + "content_type VARCHAR(200) NOT NULL, size_bytes BIGINT NOT NULL, sha256 CHAR(64) NOT NULL,"
                        + "requested_by VARCHAR(200) NOT NULL, created_at TIMESTAMPTZ NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS " + schema() + ".etl_run ("
                        + "run_id VARCHAR(64) PRIMARY KEY, project_id VARCHAR(200) NOT NULL, workflow_id VARCHAR(200) NOT NULL,"
                        + "workflow_instance_id BIGINT, manifest_uri TEXT NOT NULL, manifest_sha256 CHAR(64) NOT NULL,"
                        + "requested_by VARCHAR(200) NOT NULL, state VARCHAR(50) NOT NULL, created_at TIMESTAMPTZ NOT NULL,"
                        + "submitted_at TIMESTAMPTZ, error_message VARCHAR(2000))");
                statement.execute("ALTER TABLE " + schema() + ".etl_run ADD COLUMN IF NOT EXISTS error_message VARCHAR(2000)");
                initialized.set(true);
            } catch (SQLException exception) {
                throw new IllegalStateException("Unable to initialize ETL PostgreSQL ledger", exception);
            }
        }
    }

    private Connection connection() throws SQLException {
        var ledger = properties.getLedger();
        return DriverManager.getConnection(ledger.getJdbcUrl(), ledger.getUsername(), ledger.getPassword());
    }

    private String schema() { return properties.getLedger().getSchema(); }

    private static RunRecord runRecord(ResultSet result) throws SQLException {
        long instanceId = result.getLong(4);
        return new RunRecord(
                result.getString(1), result.getString(2), result.getString(3),
                result.wasNull() ? null : instanceId,
                result.getString(5), result.getString(6), result.getString(7), result.getString(8));
    }

    public record ArtifactRecord(String artifactId, String uri, String originalName, String contentType,
                                 long size, String sha256, String requestedBy, Instant createdAt) {}

    public record RunRecord(String runId, String projectId, String workflowId, Long workflowInstanceId,
                            String manifestUri, String manifestSha256, String requestedBy, String state) {}
}
