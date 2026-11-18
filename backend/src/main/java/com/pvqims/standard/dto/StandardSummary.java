package com.pvqims.standard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.pvqims.standard.entity.InspectionStandard;

/**
 * 对外展示的校验标准信息。
 */
public record StandardSummary(Long id, Long projectId, String scene, String processName, String itemCode,
                              String itemName, String unit, String valueType, String judgeType,
                              BigDecimal lowerLimit, BigDecimal upperLimit, BigDecimal targetValue,
                              BigDecimal tolerance, String samplingNote, Boolean isEnabled, Integer sortOrder,
                              String remark, LocalDateTime createdAt) {

    public static StandardSummary from(InspectionStandard standard) {
        return new StandardSummary(standard.getId(), standard.getProjectId(), standard.getScene(),
            standard.getProcessName(), standard.getItemCode(), standard.getItemName(), standard.getUnit(),
            standard.getValueType(), standard.getJudgeType(), standard.getLowerLimit(), standard.getUpperLimit(),
            standard.getTargetValue(), standard.getTolerance(), standard.getSamplingNote(),
            standard.getIsEnabled(), standard.getSortOrder(), standard.getRemark(), standard.getCreatedAt());
    }
}
