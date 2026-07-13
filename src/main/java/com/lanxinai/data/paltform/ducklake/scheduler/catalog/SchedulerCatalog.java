package com.lanxinai.data.paltform.ducklake.scheduler.catalog;

import java.util.List;

public record SchedulerCatalog(
        int schemaVersion,
        String generatedFrom,
        String gitRef,
        ExecutionDefaults execution,
        List<ManagedProject> projects) {

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
            String taskIdParameter,
            String paramsParameter,
            List<ManagedNode> nodes) {
    }

    public record ManagedNode(
            String id,
            String name,
            String taskId,
            long code,
            String taskType) {
    }

    public record ManagedTaskGroup(
            String id,
            String name,
            int groupId) {
    }
}
