package com.pvqims.inspection.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交校验记录请求。刻意不定义 result/inspectTime：判定与时间都由服务端决定（BR-01）。
 */
public record CreateRecordRequest(
    @NotNull(message = "批次不能为空") Long batchId,
    @NotNull(message = "检验项不能为空") Long standardId,
    BigDecimal measuredValue,
    @Size(max = 100, message = "文字结论最多 100 个字符") String textValue,
    @Size(max = 50, message = "序列号最多 50 个字符") String serialNo,
    String defectType,
    String defectGrade,
    @Size(max = 255, message = "备注最多 255 个字符") String remark) {
}
