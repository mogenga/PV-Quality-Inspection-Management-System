package com.pvqims.inspection.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 提交校验记录请求。刻意不定义 result/inspectTime：判定与时间都由服务端决定（BR-01）。
 * 缺陷两列只在 FAIL 时提供，故不加 @NotNull；@Pattern 对 null 视为通过。取值见 SRS 5.12。
 */
public record CreateRecordRequest(
    @NotNull(message = "批次不能为空") Long batchId,
    @NotNull(message = "检验项不能为空") Long standardId,
    @Digits(integer = 8, fraction = 4, message = "数值最多 8 位整数、4 位小数") BigDecimal measuredValue,
    @Size(max = 100, message = "文字结论最多 100 个字符") String textValue,
    @Size(max = 50, message = "序列号最多 50 个字符") String serialNo,
    @Size(max = 50, message = "缺陷类型最多 50 个字符")
    @Pattern(regexp = "CRACK|SOLDER|BUBBLE|DELAMINATION|LOW_POWER|APPEARANCE|OTHER",
        message = "缺陷类型不在允许的枚举内") String defectType,
    @Size(max = 10, message = "缺陷等级最多 10 个字符")
    @Pattern(regexp = "CRITICAL|MAJOR|MINOR", message = "缺陷等级只能是 CRITICAL、MAJOR 或 MINOR")
    String defectGrade,
    @Size(max = 255, message = "备注最多 255 个字符") String remark) {
}
