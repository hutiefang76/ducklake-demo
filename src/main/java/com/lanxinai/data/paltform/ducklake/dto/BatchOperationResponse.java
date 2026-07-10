package com.lanxinai.data.paltform.ducklake.dto;

import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;

import java.time.Instant;
import java.util.List;

public record BatchOperationResponse(
        String dao,
        String operation,
        String batchId,
        int requested,
        int affected,
        long totalRows,
        List<DemoRecord> batchRows,
        Instant timestamp
) {
}
