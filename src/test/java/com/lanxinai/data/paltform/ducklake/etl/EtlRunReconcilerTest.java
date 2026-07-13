package com.lanxinai.data.paltform.ducklake.etl;

import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedProject;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService;
import com.lanxinai.data.paltform.ducklake.scheduler.client.DolphinSchedulerClient;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EtlRunReconcilerTest {

    private EtlPlatformProperties etlProperties;
    private SchedulerProperties schedulerProperties;
    private EtlLedgerRepository ledger;
    private SchedulerCatalogService catalogService;
    private DolphinSchedulerClient client;
    private EtlRunReconciler reconciler;

    @BeforeEach
    void setUp() {
        etlProperties = new EtlPlatformProperties();
        etlProperties.setEnabled(true);
        etlProperties.getArtifact().setEndpoint("http://s3");
        etlProperties.getArtifact().setAccessKey("test");
        etlProperties.getArtifact().setSecretKey("test");
        etlProperties.getLedger().setJdbcUrl("jdbc:postgresql://db/test");
        etlProperties.getLedger().setUsername("test");
        etlProperties.getLedger().setPassword("test");
        schedulerProperties = new SchedulerProperties();
        schedulerProperties.setEnabled(true);
        ledger = mock(EtlLedgerRepository.class);
        catalogService = mock(SchedulerCatalogService.class);
        client = mock(DolphinSchedulerClient.class);
        reconciler = new EtlRunReconciler(
                etlProperties, schedulerProperties, ledger, catalogService, client);
    }

    @Test
    void reconcilesOnlyCandidatesAndPersistsTerminalState() {
        var candidate = new EtlLedgerRepository.ReconciliationCandidate(
                "run-1", "project-a", 101L, "RUNNING_EXECUTION");
        ManagedProject project = new ManagedProject("project-a", "Project A", 42L, List.of(), List.of());
        when(ledger.findReconciliationCandidates(100)).thenReturn(List.of(candidate));
        when(catalogService.project("project-a")).thenReturn(project);
        when(client.runStatus(project, 101L)).thenReturn(status("SUCCESS"));

        reconciler.reconcilePendingRuns();

        verify(ledger).updateRunState("project-a", 101L, "SUCCESS", true);
    }

    @Test
    void continuesWhenOneCandidateFails() {
        var first = new EtlLedgerRepository.ReconciliationCandidate("run-1", "project-a", 101L, "SUBMITTED");
        var second = new EtlLedgerRepository.ReconciliationCandidate("run-2", "project-a", 102L, "SUBMITTED");
        ManagedProject project = new ManagedProject("project-a", "Project A", 42L, List.of(), List.of());
        when(ledger.findReconciliationCandidates(100)).thenReturn(List.of(first, second));
        when(catalogService.project("project-a")).thenReturn(project);
        when(client.runStatus(project, 101L)).thenThrow(new IllegalStateException("temporary"));
        when(client.runStatus(project, 102L)).thenReturn(status("FAILURE"));

        reconciler.reconcilePendingRuns();

        verify(ledger).updateRunState("project-a", 102L, "FAILURE", true);
    }

    @Test
    void doesNothingWhenReconciliationIsDisabled() {
        etlProperties.getReconciliation().setEnabled(false);

        reconciler.reconcilePendingRuns();

        verify(ledger, never()).findReconciliationCandidates(100);
    }

    private static RunStatusResponse status(String state) {
        return new RunStatusResponse(
                "project-a", 101L, "run", state, null, null, null,
                false, false, null, null);
    }
}
