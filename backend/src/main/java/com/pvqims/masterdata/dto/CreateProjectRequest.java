package com.pvqims.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
    @NotBlank @Size(max = 50) String projectCode,
    @NotBlank @Size(max = 100) String projectName,
    @Size(max = 50) String productModel,
    @Size(max = 100) String customerName,
    @Size(max = 100) String manufacturerName,
    @Size(max = 100) String location,
    @NotBlank @Pattern(regexp = "ACTIVE|CLOSED", message = "项目状态只能是 ACTIVE 或 CLOSED")
    String status,
    @Size(max = 255) String remark) {
}
