package com.lanxinai.data.paltform.ducklake.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EtlSchedulerRunRegistryTest {

    private EtlLedgerRepository ledger;
    private EtlPlatformProperties properties;
    private EtlSchedulerRunRegistry registry;

    @BeforeEach
    void setUp() {
        ledger = mock(EtlLedgerRepository.class);
        properties = new EtlPlatformProperties();
        properties.getReconciliation().setReadyStopStaleAfter(Duration.ofMinutes(2));
        registry = new EtlSchedulerRunRegistry(ledger, properties);
    }

    @Test
    void marksOldReadyStopAsRequiringOperatorAttention() {
        Instant changedAt = Instant.now().minus(Duration.ofMinutes(3));
        when(ledger.updateRunState("project-a", 101L, "READY_STOP", false))
                .thenReturn(new EtlLedgerRepository.RunStateRecord(
                        "READY_STOP", changedAt, Instant.now(), null));

        var metadata = registry.recordStatus("project-a", 101L, "ready_stop");

        assertThat(metadata.terminal()).isFalse();
        assertThat(metadata.attentionRequired()).isTrue();
        assertThat(metadata.attentionReason()).contains("master failover");
        assertThat(metadata.stateChangedAt()).isEqualTo(changedAt.toString());
    }

    @Test
    void recordsSuccessAsTerminalWithoutAttention() {
        Instant now = Instant.now();
        when(ledger.updateRunState("project-a", 102L, "SUCCESS", true))
                .thenReturn(new EtlLedgerRepository.RunStateRecord("SUCCESS", now, now, now));

        var metadata = registry.recordStatus("project-a", 102L, "success");

        assertThat(metadata.terminal()).isTrue();
        assertThat(metadata.attentionRequired()).isFalse();
        assertThat(metadata.attentionReason()).isNull();
    }
}
