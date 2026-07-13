package com.lanxinai.data.paltform.ducklake.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxinai.data.paltform.ducklake.etl.EtlArtifactService.StoredObject;
import com.lanxinai.data.paltform.ducklake.etl.EtlLedgerRepository.ArtifactRecord;
import com.lanxinai.data.paltform.ducklake.etl.EtlOrchestrationService.MaterialRefreshRequest;
import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerFacadeService;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EtlOrchestrationServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private EtlArtifactService artifacts;
    private EtlLedgerRepository ledger;
    private SchedulerFacadeService scheduler;
    private EtlOrchestrationService service;

    @BeforeEach
    void setUp() {
        artifacts = mock(EtlArtifactService.class);
        ledger = mock(EtlLedgerRepository.class);
        scheduler = mock(SchedulerFacadeService.class);
        EtlPlatformProperties properties = new EtlPlatformProperties();
        properties.getMaterialMaster().setProjectId("material-project");
        properties.getMaterialMaster().setWorkflowId("material-workflow");
        service = new EtlOrchestrationService(
                mapper, artifacts, ledger, scheduler, new EtlParameterValidator(), properties);
        when(scheduler.parameterSchema("material-project", "material-workflow"))
                .thenReturn(Map.of(
                        "delta_uri", Map.of("type", "uri", "required", true,
                                "allowed_schemes", List.of("s3", "file")),
                        "plan_id", Map.of("type", "string", "required", true),
                        "version_id", Map.of("type", "string", "required", true),
                        "mode", Map.of(
                                "type", "string", "required", false, "default", "full"),
                        "business_date", Map.of(
                                "type", "date", "required", true,
                                "default_from", "runtime.business_date")));
    }

    @Test
    void resolvesArtifactBuildsImmutableManifestAndStartsConfiguredWorkflow() throws Exception {
        when(artifacts.requireArtifact("art-1")).thenReturn(new ArtifactRecord(
                "art-1", "s3://dp-springboot-files/input/material.parquet", "material.parquet",
                "application/x-parquet", 123L, "input-sha", "doctor", Instant.parse("2026-07-13T00:00:00Z")));
        when(artifacts.putJson(anyString(), anyString(), any(byte[].class)))
                .thenReturn(new StoredObject("s3://dp-springboot-files/run-manifests/run.json", "manifest-sha"));
        when(scheduler.startWorkflow(anyString(), anyString(), any(RunManifestRef.class)))
                .thenReturn(new RunResponse("material-project", "material-workflow", null, null, 9001L));

        var result = service.startMaterialRefresh(
                new MaterialRefreshRequest("art-1", null, "plan-7", "v3", "manual test"), "doctor");

        assertThat(result.scheduler().workflowInstanceId()).isEqualTo(9001L);
        ArgumentCaptor<byte[]> manifestBytes = ArgumentCaptor.forClass(byte[].class);
        verify(artifacts).putJson(org.mockito.ArgumentMatchers.eq("run-manifests"), anyString(), manifestBytes.capture());
        JsonNode manifest = mapper.readTree(manifestBytes.getValue());
        assertThat(manifest.path("parameters").path("delta_uri").asText())
                .isEqualTo("s3://dp-springboot-files/input/material.parquet");
        assertThat(manifest.path("parameters").path("plan_id").asText()).isEqualTo("plan-7");
        assertThat(manifest.path("parameters").path("version_id").asText()).isEqualTo("v3");
        assertThat(manifest.path("parameters").path("mode").asText()).isEqualTo("full");
        assertThat(manifest.path("parameters").path("business_date").asText())
                .isEqualTo(manifest.path("runtime").path("business_date").asText());
        assertThat(manifest.path("runtime").path("request_date").asText()).isNotBlank();
        verify(ledger).createRun(anyString(), org.mockito.ArgumentMatchers.eq("material-project"),
                org.mockito.ArgumentMatchers.eq("material-workflow"), anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq("doctor"));
        verify(ledger).attachWorkflowInstance(result.runId(), 9001L);
    }

    @Test
    void marksLedgerFailedWhenSchedulerRejectsSubmission() {
        when(artifacts.putJson(anyString(), anyString(), any(byte[].class)))
                .thenReturn(new StoredObject("s3://bucket/run.json", "manifest-sha"));
        when(scheduler.startWorkflow(anyString(), anyString(), any(RunManifestRef.class)))
                .thenThrow(new IllegalStateException("DolphinScheduler rejected request"));

        assertThatThrownBy(() -> service.startMaterialRefresh(
                new MaterialRefreshRequest(null, "s3://bucket/input.parquet", "p1", "v1", null), "doctor"))
                .hasMessageContaining("rejected");

        ArgumentCaptor<String> runId = ArgumentCaptor.forClass(String.class);
        verify(ledger).markRunFailed(runId.capture(), any(IllegalStateException.class));
        assertThat(runId.getValue()).startsWith("run_");
    }
}
