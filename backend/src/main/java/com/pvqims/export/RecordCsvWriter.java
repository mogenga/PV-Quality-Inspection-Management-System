package com.pvqims.export;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.pvqims.inspection.entity.InspectionRecord;

/**
 * 校验记录 CSV 渲染（FR-15）。全静态、无 Spring 依赖，边界值由纯函数测试直接覆盖。
 * 带 UTF-8 BOM 供中文 Windows 的 Excel 双击识别；行分隔符 CRLF；
 * 文本字段遵循 RFC 4180 转义，并对以 = + - @ 开头的单元格加前缀防公式注入。
 */
public final class RecordCsvWriter {

    private static final String BOM = "﻿";
    private static final String CRLF = "\r\n";

    private static final String HEADER =
        "记录ID,检验时间,项目ID,批次ID,场景,工序,检验项,序列号,实测值,结论,"
            + "标准下限,标准上限,标准值,公差,单位,判定,是否作废,作废原因,备注";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, String> SCENE_LABELS = Map.of(
        "IQC", "来料检验", "PQC", "过程检验", "OQC", "出货检验");

    private static final Map<String, String> RESULT_LABELS = Map.of(
        "PASS", "合格", "FAIL", "不合格");

    private RecordCsvWriter() {
    }

    public static String write(List<InspectionRecord> records) {
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append(CRLF);
        for (InspectionRecord record : records) {
            appendRow(csv, record);
        }
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, InspectionRecord record) {
        csv.append(number(record.getId())).append(',')
            .append(text(record.getInspectTime() == null ? null : record.getInspectTime().format(TIME_FORMAT)))
            .append(',')
            .append(number(record.getProjectId())).append(',')
            .append(number(record.getBatchId())).append(',')
            .append(text(label(SCENE_LABELS, record.getScene()))).append(',')
            .append(text(record.getProcessName())).append(',')
            .append(text(record.getItemName())).append(',')
            .append(text(record.getSerialNo())).append(',')
            .append(number(record.getMeasuredValue())).append(',')
            .append(text(record.getTextValue())).append(',')
            .append(number(record.getStdLower())).append(',')
            .append(number(record.getStdUpper())).append(',')
            .append(number(record.getStdTarget())).append(',')
            .append(number(record.getStdTolerance())).append(',')
            .append(text(record.getStdUnit())).append(',')
            .append(text(label(RESULT_LABELS, record.getResult()))).append(',')
            .append(text(record.getIsVoid() != null && record.getIsVoid() ? "是" : "否")).append(',')
            .append(text(record.getVoidReason())).append(',')
            .append(text(record.getRemark())).append(CRLF);
    }

    /** 数值列用 toPlainString：BigDecimal.toString 会输出 1E+3 这类科学计数法。负号是符号，不加前缀。 */
    private static String number(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String number(Long value) {
        return value == null ? "" : value.toString();
    }

    /** Map.of 的 getOrDefault 遇到 null 键会抛 NPE，故先判空；未知取值原样输出。 */
    private static String label(Map<String, String> labels, String value) {
        return value == null ? null : labels.getOrDefault(value, value);
    }

    /**
     * 文本单元格：= + - @ 与制表符、回车开头的值前置单引号，防止 Excel 当公式执行；
     * 随后按 RFC 4180 处理逗号、双引号与换行。
     */
    private static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
            || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }
}
