package com.lanxinai.data.paltform.ducklake.etl;

import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerRunStates;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService;
import com.lanxinai.data.paltform.ducklake.scheduler.client.DolphinSchedulerClient;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EtlRunReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(EtlRunReconciler.class);

    private final EtlPlatformProperties etlProperties;
    private final SchedulerProperties schedulerProperties;
    private final EtlLedgerRepository ledger;
    private final SchedulerCatalogService catalogService;
    private final DolphinSchedulerClient client;

    public EtlRunReconciler(
            EtlPlatformProperties etlProperties,
            SchedulerProperties schedulerProperties,
            EtlLedgerRepository ledger,
            SchedulerCatalogService catalogService,
            DolphinSchedulerClient client) {
        this.etlProperties = etlProperties;
        this.schedulerProperties = schedulerProperties;
        this.ledger = ledger;
        this.catalogService = catalogService;
        this.client = client;
    }

    @Scheduled(fixedDelayString = "${etl-platform.reconciliation.fixed-delay:30s}")
    public void reconcilePendingRuns() {
        if (!etlProperties.isEnabled() || !schedulerProperties.isEnabled()
                || !etlProperties.getReconciliation().isEnabled()) {
            return;
        }
        etlProperties.requireAvailable();
        int batchSize = etlProperties.getReconciliation().getBatchSize();
        for (var candidate : ledger.findReconciliationCandidates(batchSize)) {
            try {
                var status = client.runStatus(
                        catalogService.project(candidate.projectId()), candidate.workflowInstanceId());
                String state = SchedulerRunStates.normalize(status.state());
                ledger.updateRunState(
                        candidate.projectId(), candidate.workflowInstanceId(), state,
                        SchedulerRunStates.isTerminal(state));
            } catch (RuntimeException exception) {
                LOG.warn(
                        "ETL run reconciliation failed: runId={}, projectId={}, workflowInstanceId={}, errorType={}",
                        candidate.runId(), candidate.projectId(), candidate.workflowInstanceId(),
                        exception.getClass().getSimpleName());
            }
        }
    }
}
