package com.lanxinai.data.paltform.ducklake.scheduler;

import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService.ResolvedRun;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService.ResolvedTaskGroup;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService.ResolvedWorkflow;
import com.lanxinai.data.paltform.ducklake.scheduler.client.DolphinSchedulerClient;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.OperationResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.ParameterSchemaResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.QueueResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunLogResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunRequest;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunStatusResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunTasksResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.SchedulerMetaResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerRunRegistry.RunStateMetadata;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SchedulerFacadeService {

    private final SchedulerProperties properties;
    private final SchedulerCatalogService catalogService;
    private final DolphinSchedulerClient client;
    private final SchedulerRunRegistry runRegistry;

    public SchedulerFacadeService(
            SchedulerProperties properties,
            SchedulerCatalogService catalogService,
            DolphinSchedulerClient client,
            SchedulerRunRegistry runRegistry) {
        this.properties = properties;
        this.catalogService = catalogService;
        this.client = client;
        this.runRegistry = runRegistry;
    }

    public SchedulerMetaResponse meta() {
        return new SchedulerMetaResponse(
                properties.isEnabled(),
                properties.authMode(),
                properties.getCatalogPath() != null,
                List.of(
                        "GET /api/scheduler/catalog",
                        "GET /api/scheduler/projects/{projectId}/workflows/{workflowId}/parameter-schema",
                        "POST /api/scheduler/projects/{projectId}/workflows/{workflowId}/runs",
                        "GET /api/scheduler/projects/{projectId}/runs/{instanceId}/status",
                        "GET /api/scheduler/projects/{projectId}/runs/{instanceId}/tasks",
                        "GET /api/scheduler/projects/{projectId}/runs/{instanceId}/log",
                        "POST /api/scheduler/projects/{projectId}/runs/{instanceId}/stop",
                        "GET /api/scheduler/projects/{projectId}/task-groups/{taskGroupId}/queue"));
    }

    public SchedulerCatalog catalog() {
        return catalogService.load();
    }

    public Map<String, Map<String, Object>> parameterSchema(String projectId, String workflowId) {
        return parameterSchemaDescriptor(projectId, workflowId).parameterSchema();
    }

    public ParameterSchemaResponse parameterSchemaDescriptor(String projectId, String workflowId) {
        ResolvedWorkflow resolved = catalogService.resolveWorkflow(projectId, workflowId);
        var workflow = resolved.workflow();
        return new ParameterSchemaResponse(
                resolved.project().id(),
                workflow.id(),
                workflow.parameterSchemaVersion(),
                workflow.parameterSchemaSha256(),
                java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(workflow.parameterSchema())));
    }

    public RunResponse startRun(String projectId, String workflowId, RunRequest request) {
        if (request == null || request.nodeId() == null || request.nodeId().isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        ResolvedRun resolved = catalogService.resolveRun(projectId, workflowId, request.nodeId());
        RunManifestRef manifest = new RunManifestRef(
                null, request.runManifestUri(), request.runManifestSha256());
        String runId = runRegistry.requireAuthorizedManifest(projectId, workflowId, manifest);
        RunResponse response = client.startRun(
                resolved.execution(), resolved.project(), resolved.workflow(), resolved.node(),
                manifest);
        runRegistry.attachWorkflowInstance(runId, response.workflowInstanceId());
        return response;
    }

    public RunResponse startWorkflow(String projectId, String workflowId, RunManifestRef manifest) {
        ResolvedWorkflow resolved = catalogService.resolveWorkflow(projectId, workflowId);
        String runId = runRegistry.requireAuthorizedManifest(projectId, workflowId, manifest);
        RunResponse response = client.startWorkflow(
                resolved.execution(), resolved.project(), resolved.workflow(), manifest);
        runRegistry.attachWorkflowInstance(runId, response.workflowInstanceId());
        return response;
    }

    public RunStatusResponse status(String projectId, long instanceId) {
        runRegistry.requireOwnedInstance(projectId, instanceId);
        RunStatusResponse status = client.runStatus(catalogService.project(projectId), instanceId);
        RunStateMetadata metadata = runRegistry.recordStatus(projectId, instanceId, status.state());
        return new RunStatusResponse(
                status.projectId(), status.workflowInstanceId(), status.name(), status.state(),
                status.submitTime(), status.startTime(), status.endTime(),
                metadata.terminal(), metadata.attentionRequired(), metadata.attentionReason(),
                metadata.stateChangedAt());
    }

    public RunTasksResponse tasks(String projectId, long instanceId) {
        runRegistry.requireOwnedInstance(projectId, instanceId);
        return client.runTasks(catalogService.project(projectId), instanceId);
    }

    public RunLogResponse log(
            String projectId,
            long instanceId,
            Long taskInstanceId,
            int skipLineNum,
            int limit) {
        runRegistry.requireOwnedInstance(projectId, instanceId);
        return client.runLog(
                catalogService.project(projectId), instanceId, taskInstanceId, skipLineNum, limit);
    }

    public OperationResponse stop(String projectId, long instanceId) {
        runRegistry.requireOwnedInstance(projectId, instanceId);
        return client.stopRun(catalogService.project(projectId), instanceId);
    }

    public QueueResponse queue(
            String projectId,
            String taskGroupId,
            Integer status,
            int pageNo,
            int pageSize) {
        ResolvedTaskGroup resolved = catalogService.resolveTaskGroup(projectId, taskGroupId);
        return client.taskGroupQueue(
                resolved.project(), resolved.taskGroup(), status, pageNo, pageSize);
    }
}
