package com.lanxinai.data.paltform.ducklake.etl;

import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerAuthorizationException;
import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerRunRegistry;
import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerRunStates;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef;
import org.springframework.stereotype.Component;

@Component
public class EtlSchedulerRunRegistry implements SchedulerRunRegistry {

    private final EtlLedgerRepository ledger;
    private final EtlPlatformProperties properties;

    public EtlSchedulerRunRegistry(EtlLedgerRepository ledger, EtlPlatformProperties properties) {
        this.ledger = ledger;
        this.properties = properties;
    }

    @Override
    public String requireAuthorizedManifest(
            String projectId,
            String workflowId,
            RunManifestRef manifest) {
        if (manifest == null || manifest.uri() == null || manifest.uri().isBlank()
                || manifest.sha256() == null || manifest.sha256().isBlank()) {
            throw new SchedulerAuthorizationException("Run manifest is not registered by the facade");
        }
        return ledger.findRunForManifest(
                        manifest.runId(), projectId, workflowId, manifest.uri(), manifest.sha256())
                .map(EtlLedgerRepository.RunRecord::runId)
                .orElseThrow(() -> new SchedulerAuthorizationException(
                        "Run manifest is not registered by the facade"));
    }

    @Override
    public void attachWorkflowInstance(String runId, long workflowInstanceId) {
        ledger.attachWorkflowInstance(runId, workflowInstanceId);
    }

    @Override
    public void requireOwnedInstance(String projectId, long workflowInstanceId) {
        if (ledger.findRunByWorkflowInstance(projectId, workflowInstanceId).isEmpty()) {
            throw new SchedulerAuthorizationException(
                    "Workflow instance is not registered by the facade");
        }
    }

    @Override
    public RunStateMetadata recordStatus(
            String projectId,
            long workflowInstanceId,
            String schedulerState) {
        String normalizedState = SchedulerRunStates.normalize(schedulerState);
        boolean terminal = SchedulerRunStates.isTerminal(normalizedState);
        var state = ledger.updateRunState(projectId, workflowInstanceId, normalizedState, terminal);
        boolean staleReadyStop = "READY_STOP".equals(state.state())
                && state.stateChangedAt()
                        .plus(properties.getReconciliation().getReadyStopStaleAfter())
                        .isBefore(java.time.Instant.now());
        return new RunStateMetadata(
                terminal,
                staleReadyStop,
                staleReadyStop
                        ? "READY_STOP has not converged; operator inspection or master failover may be required"
                        : null,
                state.stateChangedAt().toString());
    }
}
