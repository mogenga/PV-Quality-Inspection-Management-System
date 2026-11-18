package com.pvqims.standard.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 标准不允许换项目：检验记录里冗余存了 project_id，换项目会让历史数据错位。
 */
public record UpdateStandardRequest(
    @NotBlank @Pattern(regexp = "IQC|PQC|OQC", message = "场景只能是 IQC、PQC 或 OQC")
    String scene,
    @NotBlank @Size(max = 50) String processName,
    @NotBlank @Size(max = 30) String itemCode,
    @NotBlank @Size(max = 100) String itemName,
    @Size(max = 20) String unit,
    @NotBlank @Pattern(regexp = "NUMERIC|TEXT", message = "值类型只能是 NUMERIC 或 TEXT")
    String valueType,
    @NotBlank @Pattern(regexp = "GT|LT|RANGE|STD_TOL|PASS_FAIL", message = "判定方式只能是 GT、LT、RANGE、STD_TOL 或 PASS_FAIL")
    String judgeType,
    @Digits(integer = 8, fraction = 4, message = "数值最多 8 位整数、4 位小数") BigDecimal lowerLimit,
    @Digits(integer = 8, fraction = 4, message = "数值最多 8 位整数、4 位小数") BigDecimal upperLimit,
    @Digits(integer = 8, fraction = 4, message = "数值最多 8 位整数、4 位小数") BigDecimal targetValue,
    @Digits(integer = 8, fraction = 4, message = "数值最多 8 位整数、4 位小数") BigDecimal tolerance,
    @Size(max = 100) String samplingNote,
    @NotNull Boolean isEnabled,
    @NotNull @Min(0) Integer sortOrder,
    @Size(max = 255) String remark) {
}
