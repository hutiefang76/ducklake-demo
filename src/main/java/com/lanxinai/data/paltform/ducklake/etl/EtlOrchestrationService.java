package com.lanxinai.data.paltform.ducklake.etl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxinai.data.paltform.ducklake.etl.EtlArtifactService.StoredObject;
import com.lanxinai.data.paltform.ducklake.etl.EtlLedgerRepository.ArtifactRecord;
import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerFacadeService;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EtlOrchestrationService {

    private final ObjectMapper mapper;
    private final EtlArtifactService artifacts;
    private final EtlLedgerRepository ledger;
    private final SchedulerFacadeService scheduler;
    private final EtlParameterValidator parameterValidator;
    private final EtlPlatformProperties properties;

    public EtlOrchestrationService(ObjectMapper mapper, EtlArtifactService artifacts,
                                   EtlLedgerRepository ledger, SchedulerFacadeService scheduler,
                                   EtlParameterValidator parameterValidator,
                                   EtlPlatformProperties properties) {
        this.mapper = mapper;
        this.artifacts = artifacts;
        this.ledger = ledger;
        this.scheduler = scheduler;
        this.parameterValidator = parameterValidator;
        this.properties = properties;
    }

    public SubmittedRun startMaterialRefresh(MaterialRefreshRequest request, String requestedBy) {
        if (request == null || blank(request.planId()) || blank(request.versionId())) {
            throw new IllegalArgumentException("planId and versionId are required");
        }
        String deltaUri;
        if (!blank(request.artifactId()) && !blank(request.deltaUri())) {
            throw new IllegalArgumentException("Use artifactId or deltaUri, not both");
        }
        if (!blank(request.artifactId())) {
            ArtifactRecord artifact = artifacts.requireArtifact(request.artifactId());
            deltaUri = artifact.uri();
        } else if (!blank(request.deltaUri())) {
            deltaUri = request.deltaUri().trim();
        } else {
            throw new IllegalArgumentException("artifactId or deltaUri is required");
        }
        if (!deltaUri.startsWith("s3://") && !deltaUri.startsWith("file://")) {
            throw new IllegalArgumentException("deltaUri must use s3:// or file://");
        }
        return start(
                properties.getMaterialMaster().getProjectId(),
                properties.getMaterialMaster().getWorkflowId(),
                Map.of("delta_uri", deltaUri, "plan_id", request.planId().trim(),
                        "version_id", request.versionId().trim()),
                requestedBy,
                request.reason());
    }

    public SubmittedRun start(String projectId, String workflowId, Map<String, Object> parameters,
                              String requestedBy, String reason) {
        Map<String, Object> rawParameters = parameters == null ? Map.of() : parameters;
        String identity = blank(requestedBy) ? "demo-user" : requestedBy.trim();
        if (identity.length() > 200) throw new IllegalArgumentException("requestedBy must not exceed 200 characters");
        Instant requestTimestamp = Instant.now();
        String requestDate = requestTimestamp.atZone(ZoneOffset.UTC).toLocalDate().toString();
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("business_date", requestDate);
        runtime.put("request_date", requestDate);
        runtime.put("request_timestamp", requestTimestamp.toString());
        runtime.put("requested_by", identity);
        runtime.put("reason", reason == null ? "" : reason);
        Map<String, Object> supplied = parameterValidator.validate(
                scheduler.parameterSchema(projectId, workflowId), rawParameters, runtime);
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema_version", 1);
        manifest.put("kind", "etl_run_manifest");
        manifest.put("run_id", runId);
        manifest.put("parameters", supplied);
        manifest.put("runtime", runtime);
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(manifest);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("ETL parameters must be JSON serializable", exception);
        }
        StoredObject stored = artifacts.putJson("run-manifests", runId, bytes);
        ledger.createRun(runId, projectId, workflowId, stored.uri(), stored.sha256(), identity);
        try {
            RunResponse response = scheduler.startWorkflow(
                    projectId, workflowId, new RunManifestRef(runId, stored.uri(), stored.sha256()));
            ledger.attachWorkflowInstance(runId, response.workflowInstanceId());
            return new SubmittedRun(runId, stored.uri(), stored.sha256(), response);
        } catch (RuntimeException failure) {
            try {
                ledger.markRunFailed(runId, failure);
            } catch (RuntimeException ledgerFailure) {
                failure.addSuppressed(ledgerFailure);
            }
            throw failure;
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record MaterialRefreshRequest(String artifactId, String deltaUri, String planId,
                                         String versionId, String reason) {}
    public record GenericRunRequest(Map<String, Object> parameters, String reason) {}
    public record SubmittedRun(String runId, String runManifestUri, String runManifestSha256,
                               RunResponse scheduler) {}
}
