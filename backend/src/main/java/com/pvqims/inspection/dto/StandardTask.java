package com.pvqims.inspection.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 录入页的待检项：标准信息 + 该批次上一次实测值（无记录时三项均为 null）。
 */
public record StandardTask(Long standardId, String scene, String processName, String itemCode, String itemName,
                           String unit, String valueType, String judgeType, BigDecimal lowerLimit,
                           BigDecimal upperLimit, BigDecimal targetValue, BigDecimal tolerance, Integer sortOrder,
                           BigDecimal lastMeasuredValue, String lastResult, LocalDateTime lastInspectTime) {
}
