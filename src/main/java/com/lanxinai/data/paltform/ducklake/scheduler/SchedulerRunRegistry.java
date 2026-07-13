package com.lanxinai.data.paltform.ducklake.scheduler;

import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef;

public interface SchedulerRunRegistry {

    String requireAuthorizedManifest(String projectId, String workflowId, RunManifestRef manifest);

    void attachWorkflowInstance(String runId, long workflowInstanceId);

    void requireOwnedInstance(String projectId, long workflowInstanceId);

    RunStateMetadata recordStatus(String projectId, long workflowInstanceId, String schedulerState);

    record RunStateMetadata(
            boolean terminal,
            boolean attentionRequired,
            String attentionReason,
            String stateChangedAt) {
    }
}
