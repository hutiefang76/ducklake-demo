package com.lanxinai.data.paltform.ducklake.scheduler.dto;

import java.util.List;
import java.util.Map;

public final class SchedulerDtos {

    private SchedulerDtos() {
    }

    public record SchedulerMetaResponse(
            boolean enabled,
            String authMode,
            boolean catalogConfigured,
            List<String> endpoints) {
    }

    public record RunRequest(String nodeId, String runManifestUri, String runManifestSha256) {
    }

    public record RunManifestRef(String runId, String uri, String sha256) {
    }

    public record ParameterSchemaResponse(
            String projectId,
            String workflowId,
            int schemaVersion,
            String sha256,
            Map<String, Map<String, Object>> parameterSchema) {
    }

    public record RunResponse(
            String projectId,
            String workflowId,
            String nodeId,
            String taskId,
            long workflowInstanceId) {
    }

    public record RunStatusResponse(
            String projectId,
            long workflowInstanceId,
            String name,
            String state,
            String submitTime,
            String startTime,
            String endTime) {
    }

    public record TaskInstanceSummary(
            long id,
            String name,
            long taskCode,
            String taskType,
            String state,
            String submitTime,
            String startTime,
            String endTime) {
    }

    public record RunTasksResponse(
            String projectId,
            long workflowInstanceId,
            List<TaskInstanceSummary> tasks) {
    }

    public record RunLogResponse(
            String projectId,
            long workflowInstanceId,
            long taskInstanceId,
            int skipLineNum,
            int limit,
            String message) {
    }

    public record OperationResponse(
            String operation,
            String projectId,
            long workflowInstanceId,
            boolean accepted) {
    }

    public record QueueItem(
            int snapshotIndex,
            int queueId,
            int taskInstanceId,
            String taskName,
            int workflowInstanceId,
            String workflowInstanceName,
            int priority,
            String status,
            String createTime,
            String updateTime) {
    }

    public record QueueResponse(
            String projectId,
            String taskGroupId,
            String taskGroupName,
            int total,
            boolean exactPosition,
            String ordering,
            List<QueueItem> items) {
    }
}
