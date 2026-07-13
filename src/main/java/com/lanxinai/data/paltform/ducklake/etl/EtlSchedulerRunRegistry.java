package com.lanxinai.data.paltform.ducklake.etl;

import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerAuthorizationException;
import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerRunRegistry;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef;
import org.springframework.stereotype.Component;

@Component
public class EtlSchedulerRunRegistry implements SchedulerRunRegistry {

    private final EtlLedgerRepository ledger;

    public EtlSchedulerRunRegistry(EtlLedgerRepository ledger) {
        this.ledger = ledger;
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
}
