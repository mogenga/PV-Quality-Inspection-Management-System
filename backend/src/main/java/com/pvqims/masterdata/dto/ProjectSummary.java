package com.pvqims.masterdata.dto;

import java.time.LocalDateTime;

import com.pvqims.masterdata.entity.Project;

/**
 * 对外展示的项目信息。
 */
public record ProjectSummary(Long id, String projectCode, String projectName, String productModel,
                             String customerName, String manufacturerName, String location,
                             String status, String remark, LocalDateTime createdAt) {

    public static ProjectSummary from(Project project) {
        return new ProjectSummary(project.getId(), project.getProjectCode(), project.getProjectName(),
            project.getProductModel(), project.getCustomerName(), project.getManufacturerName(),
            project.getLocation(), project.getStatus(), project.getRemark(), project.getCreatedAt());
    }
}
