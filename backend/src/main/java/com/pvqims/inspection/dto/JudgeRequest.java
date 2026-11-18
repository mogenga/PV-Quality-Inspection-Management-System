package com.pvqims.inspection.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 判定预览请求：只带标准与实测值，不落库。
 */
public record JudgeRequest(
    @NotNull(message = "检验项不能为空") Long standardId,
    BigDecimal measuredValue,
    @Size(max = 100, message = "文字结论最多 100 个字符") String textValue) {
}
