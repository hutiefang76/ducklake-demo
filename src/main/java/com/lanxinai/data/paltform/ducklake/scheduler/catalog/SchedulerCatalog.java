package com.lanxinai.data.paltform.ducklake.scheduler.catalog;

import java.util.List;
import java.util.Map;

public record SchedulerCatalog(
        int schemaVersion,
        String generatedFrom,
        String gitRef,
        ExecutionDefaults execution,
        List<TaskContract> tasks,
        LineageCatalog lineage,
        List<ManagedProject> projects) {

    public record TaskContract(
            String id,
            String title,
            String description,
            String path,
            String entrypoint,
            List<String> parameter_profiles,
            Map<String, Map<String, Object>> parameters,
            Map<String, Object> data_contract,
            Map<String, Object> execution) {
    }

    public record LineageCatalog(
            int schemaVersion,
            List<Map<String, Object>> datasets,
            List<Map<String, Object>> taskEdges,
            List<Map<String, Object>> transformations,
            List<Map<String, Object>> workflowEdges) {
    }

    public record ExecutionDefaults(
            String tenantCode,
            String workerGroup,
            String failureStrategy,
            String warningType,
            int warningGroupId,
            String runMode,
            String workflowInstancePriority,
            long environmentCode) {
    }

    public record ManagedProject(
            String id,
            String name,
            long code,
            List<ManagedWorkflow> workflows,
            List<ManagedTaskGroup> taskGroups) {
    }

    public record ManagedWorkflow(
            String id,
            String name,
            long code,
            String runManifestParameter,
            String runManifestSha256Parameter,
            int parameterSchemaVersion,
            String parameterSchemaSha256,
            Map<String, Map<String, Object>> parameterSchema,
            List<ManagedNode> nodes) {
    }

    public record ManagedNode(
            String id,
            String name,
            String taskId,
            long code,
            String taskType,
            List<String> dependsOn,
            String title,
            String description,
            Map<String, Map<String, Object>> parameters,
            Map<String, Object> dataContract) {
    }

    public record ManagedTaskGroup(
            String id,
            String name,
            int groupId) {
    }
}
