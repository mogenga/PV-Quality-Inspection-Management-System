package com.pvqims.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 作废记录请求。作废原因必填，只作废不物理删除（BR-08）。
 */
public record VoidRecordRequest(
    @NotBlank(message = "请填写作废原因")
    @Size(max = 255, message = "作废原因最多 255 字") String reason) {
}
