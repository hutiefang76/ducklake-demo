package com.lanxinai.data.paltform.ducklake.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService;
import com.lanxinai.data.paltform.ducklake.scheduler.client.DolphinSchedulerClient;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EtlReconciliationLiveTest {

    @Test
    void readsExistingSchedulerInstanceAndPersistsTerminalLedgerState() {
        String jdbcUrl = env("ETL_LEDGER_JDBC_URL");
        String ledgerUser = env("ETL_LEDGER_USERNAME");
        String ledgerPassword = env("ETL_LEDGER_PASSWORD");
        String dsBaseUrl = env("DS_BASE_URL");
        String dsUsername = env("DS_USERNAME");
        String dsPassword = env("DS_PASSWORD");
        String catalogPath = env("ETL_SCHEDULER_CATALOG_PATH");
        String projectId = env("ETL_RECONCILIATION_LIVE_PROJECT_ID");
        String instanceText = env("ETL_RECONCILIATION_LIVE_INSTANCE_ID");
        Assumptions.assumeTrue(java.util.stream.Stream.of(
                jdbcUrl, ledgerUser, ledgerPassword, dsBaseUrl, dsUsername, dsPassword,
                catalogPath, projectId, instanceText).allMatch(EtlReconciliationLiveTest::hasText));

        long instanceId = Long.parseLong(instanceText);
        EtlPlatformProperties etl = new EtlPlatformProperties();
        etl.setEnabled(true);
        etl.getArtifact().setEndpoint("http://unused.invalid");
        etl.getArtifact().setAccessKey("unused");
        etl.getArtifact().setSecretKey("unused");
        etl.getLedger().setJdbcUrl(jdbcUrl);
        etl.getLedger().setUsername(ledgerUser);
        etl.getLedger().setPassword(ledgerPassword);

        SchedulerProperties scheduler = new SchedulerProperties();
        scheduler.setEnabled(true);
        scheduler.setBaseUrl(dsBaseUrl);
        scheduler.setUsername(dsUsername);
        scheduler.setPassword(dsPassword);
        scheduler.setCatalogPath(Path.of(catalogPath));
        scheduler.setRequestTimeout(Duration.ofSeconds(30));

        EtlLedgerRepository ledger = new EtlLedgerRepository(etl);
        SchedulerCatalogService catalog = new SchedulerCatalogService(scheduler, new ObjectMapper());
        DolphinSchedulerClient client = new DolphinSchedulerClient(
                scheduler, new ObjectMapper(), HttpClient.newHttpClient());
        EtlSchedulerRunRegistry registry = new EtlSchedulerRunRegistry(ledger, etl);

        var status = client.runStatus(catalog.project(projectId), instanceId);
        var metadata = registry.recordStatus(projectId, instanceId, status.state());
        var persisted = ledger.findRunByWorkflowInstance(projectId, instanceId).orElseThrow();

        assertThat(persisted.state()).isEqualTo(status.state());
        assertThat(metadata.terminal()).isTrue();
        assertThat(metadata.attentionRequired()).isFalse();
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
