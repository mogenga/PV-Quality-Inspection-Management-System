package com.pvqims.inspection.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.pvqims.inspection.entity.InspectionRecord;

/**
 * 对外展示的校验记录，含提交时复制的标准快照。
 */
public record InspectionRecordSummary(Long id, Long projectId, Long batchId, Long standardId, String scene,
                                      String processName, String itemName, String serialNo, Long inspectorId,
                                      LocalDateTime inspectTime, BigDecimal measuredValue, String textValue,
                                      String result, BigDecimal stdLower, BigDecimal stdUpper, BigDecimal stdTarget,
                                      BigDecimal stdTolerance, String stdUnit, Boolean isVoid, String remark,
                                      LocalDateTime createdAt) {

    public static InspectionRecordSummary from(InspectionRecord record) {
        return new InspectionRecordSummary(record.getId(), record.getProjectId(), record.getBatchId(),
            record.getStandardId(), record.getScene(), record.getProcessName(), record.getItemName(),
            record.getSerialNo(), record.getInspectorId(), record.getInspectTime(), record.getMeasuredValue(),
            record.getTextValue(), record.getResult(), record.getStdLower(), record.getStdUpper(),
            record.getStdTarget(), record.getStdTolerance(), record.getStdUnit(), record.getIsVoid(),
            record.getRemark(), record.getCreatedAt());
    }
}
