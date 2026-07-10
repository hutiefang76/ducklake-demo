package com.lanxinai.data.paltform.ducklake.dto;

import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;

import java.time.Instant;
import java.util.List;

public record ScenarioResponse(
        String dao,
        String batchId,
        int inserted,
        int updated,
        int deleted,
        long totalBefore,
        long totalAfterInsert,
        long totalAfterUpdate,
        long totalAfterDelete,
        List<DemoRecord> remainingRows,
        Instant timestamp
) {
}
