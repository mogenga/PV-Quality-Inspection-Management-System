package com.pvqims.masterdata.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.pvqims.masterdata.entity.Batch;

/**
 * 对外展示的批次信息。
 */
public record BatchSummary(Long id, Long projectId, String batchNo, Integer plannedQty,
                           LocalDate startDate, LocalDate endDate, String status, String remark,
                           LocalDateTime createdAt) {

    public static BatchSummary from(Batch batch) {
        return new BatchSummary(batch.getId(), batch.getProjectId(), batch.getBatchNo(),
            batch.getPlannedQty(), batch.getStartDate(), batch.getEndDate(), batch.getStatus(),
            batch.getRemark(), batch.getCreatedAt());
    }
}
