package com.pvqims.inspection.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 校验记录，映射 inspection_record 表。五列 std_* 是标准快照，改标准不回溯历史（BR-02）。
 */
@TableName("inspection_record")
public class InspectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long batchId;

    private Long standardId;

    private String scene;

    private String processName;

    private String itemName;

    private String serialNo;

    private Long inspectorId;

    private LocalDateTime inspectTime;

    private BigDecimal measuredValue;

    private String textValue;

    private String result;

    private BigDecimal stdLower;

    private BigDecimal stdUpper;

    private BigDecimal stdTarget;

    private BigDecimal stdTolerance;

    private String stdUnit;

    private Boolean isVoid;

    private String voidReason;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getStandardId() {
        return standardId;
    }

    public void setStandardId(Long standardId) {
        this.standardId = standardId;
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

    public Long getInspectorId() {
        return inspectorId;
    }

    public void setInspectorId(Long inspectorId) {
        this.inspectorId = inspectorId;
    }

    public LocalDateTime getInspectTime() {
        return inspectTime;
    }

    public void setInspectTime(LocalDateTime inspectTime) {
        this.inspectTime = inspectTime;
    }

    public BigDecimal getMeasuredValue() {
        return measuredValue;
    }

    public void setMeasuredValue(BigDecimal measuredValue) {
        this.measuredValue = measuredValue;
    }

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
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

    public Boolean getIsVoid() {
        return isVoid;
    }

    public void setIsVoid(Boolean isVoid) {
        this.isVoid = isVoid;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
