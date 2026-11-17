package com.pvqims.masterdata.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 批次不允许换项目：检验记录里冗余存了 project_id，换项目会让历史数据错位。
 */
public record UpdateBatchRequest(
    @NotBlank @Size(max = 50) String batchNo,
    @NotNull @Min(1) Integer plannedQty,
    LocalDate startDate,
    LocalDate endDate,
    @NotBlank @Pattern(regexp = "PLANNED|PRODUCING|COMPLETED", message = "批次状态只能是 PLANNED、PRODUCING 或 COMPLETED")
    String status,
    @Size(max = 255) String remark) {
}
