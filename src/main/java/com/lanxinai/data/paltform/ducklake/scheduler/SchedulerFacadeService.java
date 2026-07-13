package com.lanxinai.data.paltform.ducklake.scheduler;

import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService.ResolvedRun;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService.ResolvedTaskGroup;
import com.lanxinai.data.paltform.ducklake.scheduler.client.DolphinSchedulerClient;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.OperationResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.QueueResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunLogResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunRequest;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunStatusResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunTasksResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.SchedulerMetaResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SchedulerFacadeService {

    private final SchedulerProperties properties;
    private final SchedulerCatalogService catalogService;
    private final DolphinSchedulerClient client;

    public SchedulerFacadeService(
            SchedulerProperties properties,
            SchedulerCatalogService catalogService,
            DolphinSchedulerClient client) {
        this.properties = properties;
        this.catalogService = catalogService;
        this.client = client;
    }

    public SchedulerMetaResponse meta() {
        return new SchedulerMetaResponse(
                properties.isEnabled(),
                properties.authMode(),
                properties.getCatalogPath() != null,
                List.of(
                        "GET /api/scheduler/catalog",
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

    public RunResponse startRun(String projectId, String workflowId, RunRequest request) {
        if (request == null || request.nodeId() == null || request.nodeId().isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        ResolvedRun resolved = catalogService.resolveRun(projectId, workflowId, request.nodeId());
        Map<String, Object> params = request.params() == null ? Map.of() : request.params();
        return client.startRun(
                resolved.execution(), resolved.project(), resolved.workflow(), resolved.node(), params);
    }

    public RunStatusResponse status(String projectId, long instanceId) {
        return client.runStatus(catalogService.project(projectId), instanceId);
    }

    public RunTasksResponse tasks(String projectId, long instanceId) {
        return client.runTasks(catalogService.project(projectId), instanceId);
    }

    public RunLogResponse log(
            String projectId,
            long instanceId,
            Long taskInstanceId,
            int skipLineNum,
            int limit) {
        return client.runLog(
                catalogService.project(projectId), instanceId, taskInstanceId, skipLineNum, limit);
    }

    public OperationResponse stop(String projectId, long instanceId) {
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
