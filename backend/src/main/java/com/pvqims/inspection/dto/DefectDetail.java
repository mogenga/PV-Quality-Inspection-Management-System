package com.pvqims.inspection.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缺陷列表行，对应视图 v_defect_detail：缺陷字段 + 记录的标准快照 + 项目/批次/检验员/处理人名称。
 * 刻意用 POJO 而非 record——MyBatis 按 setter 映射（本项目未开启构造器参数名自动映射）。
 */
public class DefectDetail {

    private Long defectId;
    private Long recordId;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private String batchNo;
    private String scene;
    private String processName;
    private String itemName;
    private String serialNo;
    private LocalDateTime inspectTime;
    private BigDecimal stdLower;
    private BigDecimal stdUpper;
    private BigDecimal stdTarget;
    private BigDecimal stdTolerance;
    private String stdUnit;
    private String defectType;
    private String defectGrade;
    private String defectStatus;
    private String handleNote;
    private LocalDateTime resolvedAt;
    private LocalDateTime defectCreatedAt;
    private String inspectorName;
    private String handlerName;

    public Long getDefectId() {
        return defectId;
    }

    public void setDefectId(Long defectId) {
        this.defectId = defectId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public LocalDateTime getInspectTime() {
        return inspectTime;
    }

    public void setInspectTime(LocalDateTime inspectTime) {
        this.inspectTime = inspectTime;
    }

    public BigDecimal getStdLower() {
        return stdLower;
    }

    public void setStdLower(BigDecimal stdLower) {
        this.stdLower = stdLower;
    }

    public BigDecimal getStdUpper() {
        return stdUpper;
    }

    public void setStdUpper(BigDecimal stdUpper) {
        this.stdUpper = stdUpper;
    }

    public BigDecimal getStdTarget() {
        return stdTarget;
    }

    public void setStdTarget(BigDecimal stdTarget) {
        this.stdTarget = stdTarget;
    }

    public BigDecimal getStdTolerance() {
        return stdTolerance;
    }

    public void setStdTolerance(BigDecimal stdTolerance) {
        this.stdTolerance = stdTolerance;
    }

    public String getStdUnit() {
        return stdUnit;
    }

    public void setStdUnit(String stdUnit) {
        this.stdUnit = stdUnit;
    }

    public String getDefectType() {
        return defectType;
    }

    public void setDefectType(String defectType) {
        this.defectType = defectType;
    }

    public String getDefectGrade() {
        return defectGrade;
    }

    public void setDefectGrade(String defectGrade) {
        this.defectGrade = defectGrade;
    }

    public String getDefectStatus() {
        return defectStatus;
    }

    public void setDefectStatus(String defectStatus) {
        this.defectStatus = defectStatus;
    }

    public String getHandleNote() {
        return handleNote;
    }

    public void setHandleNote(String handleNote) {
        this.handleNote = handleNote;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getDefectCreatedAt() {
        return defectCreatedAt;
    }

    public void setDefectCreatedAt(LocalDateTime defectCreatedAt) {
        this.defectCreatedAt = defectCreatedAt;
    }

    public String getInspectorName() {
        return inspectorName;
    }

    public void setInspectorName(String inspectorName) {
        this.inspectorName = inspectorName;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }
}
