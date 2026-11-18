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
    @NotBlank(message = "场景不能为空") @Pattern(regexp = "IQC|PQC|OQC", message = "场景只能是 IQC、PQC 或 OQC")
    String scene,
    @NotBlank(message = "工序不能为空") @Size(max = 50, message = "工序最多 50 个字符") String processName,
    @NotBlank(message = "检验项编码不能为空") @Size(max = 30, message = "检验项编码最多 30 个字符") String itemCode,
    @NotBlank(message = "检验项名称不能为空") @Size(max = 100, message = "检验项名称最多 100 个字符") String itemName,
    @Size(max = 20, message = "单位最多 20 个字符") String unit,
    @NotBlank(message = "值类型不能为空") @Pattern(regexp = "NUMERIC|TEXT", message = "值类型只能是 NUMERIC 或 TEXT")
    String valueType,
    @NotBlank(message = "判定方式不能为空") @Pattern(regexp = "GT|LT|RANGE|STD_TOL|PASS_FAIL", message = "判定方式只能是 GT、LT、RANGE、STD_TOL 或 PASS_FAIL")
    String judgeType,
    @Digits(integer = 8, fraction = 4, message = "数值最多 8 位整数、4 位小数") BigDecimal lowerLimit,
    @Digits(integer = 8, fraction = 4, message = "数值最多 8 位整数、4 位小数") BigDecimal upperLimit,
    @Digits(integer = 8, fraction = 4, message = "数值最多 8 位整数、4 位小数") BigDecimal targetValue,
    @Digits(integer = 8, fraction = 4, message = "数值最多 8 位整数、4 位小数") BigDecimal tolerance,
    @Size(max = 100, message = "抽样说明最多 100 个字符") String samplingNote,
    @NotNull(message = "启用状态不能为空") Boolean isEnabled,
    @NotNull(message = "排序不能为空") @Min(value = 0, message = "排序不能小于 0") Integer sortOrder,
    @Size(max = 255, message = "备注最多 255 个字符") String remark) {
}
