package com.pvqims.inspection.dto;

import jakarta.validation.constraints.Size;

/**
 * 缺陷流转请求。说明可为空（认领与提交整改无须说明），
 * 关闭与验证不通过时必填，由服务层按流转类型判定（SRS 4.6）。
 */
public record DefectActionRequest(
    @Size(max = 255, message = "处理说明最多 255 字") String note) {
}
