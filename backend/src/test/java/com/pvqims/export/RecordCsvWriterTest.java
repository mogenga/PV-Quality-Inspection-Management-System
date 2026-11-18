package com.pvqims.export;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.pvqims.inspection.entity.InspectionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV 输出的纯函数测试：转义与注入防护的边界值多，逐例启动 Spring 上下文不值得。
 */
class RecordCsvWriterTest {

    private static final String HEADER =
        "记录ID,检验时间,项目ID,批次ID,场景,工序,检验项,序列号,实测值,结论,标准下限,标准上限,标准值,公差,单位,判定,是否作废,作废原因,备注";

    private static InspectionRecord record() {
        InspectionRecord record = new InspectionRecord();
        record.setId(101L);
        record.setInspectTime(LocalDateTime.of(2026, 11, 18, 9, 30, 5));
        record.setProjectId(7L);
        record.setBatchId(8L);
        record.setScene("PQC");
        record.setProcessName("层压");
        record.setItemName("功率");
        record.setSerialNo("SN-1");
        record.setMeasuredValue(new BigDecimal("710.5000"));
        record.setResult("PASS");
        record.setStdLower(new BigDecimal("700.0000"));
        record.setStdUpper(new BigDecimal("720.0000"));
        record.setStdUnit("W");
        record.setIsVoid(false);
        return record;
    }

    /** 极简 RFC 4180 解析：跳过 BOM，按字符扫描，供断言还原被转义的原值。 */
    private static List<List<String>> parse(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 1; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                i++;
            } else {
                field.append(c);
            }
        }
        return rows;
    }

    @Test
    void csvStartsWithBomAndUsesCrLf() {
        String csv = RecordCsvWriter.write(List.of(record()));

        // 中文 Windows 的 Excel 靠 BOM 识别 UTF-8，缺了就是乱码。
        assertThat(csv).startsWith("﻿");
        // 除 BOM 外，抹掉 CRLF 后不应再有任何孤立换行符。
        assertThat(csv.substring(1).replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
        assertThat(csv).endsWith("\r\n");
        assertThat(parse(csv)).hasSize(2);
    }

    @Test
    void headerRowHasExpectedColumns() {
        String csv = RecordCsvWriter.write(List.of());

        String[] lines = csv.substring(1).split("\r\n");
        assertThat(lines).containsExactly(HEADER);
        assertThat(lines[0].split(",")).hasSize(19);
    }

    @Test
    void csvEscapesCommaQuoteAndNewline() {
        InspectionRecord record = record();
        record.setItemName("外观,尺寸");
        record.setSerialNo("line1\nline2");
        record.setRemark("a\"b");

        String csv = RecordCsvWriter.write(List.of(record));

        // 含逗号/引号/换行的字段整体加双引号，内部引号成对加倍。
        assertThat(csv).contains("\"外观,尺寸\"");
        assertThat(csv).contains("\"line1\nline2\"");
        assertThat(csv).contains("\"a\"\"b\"");

        List<String> row = parse(csv).get(1);
        assertThat(row.get(6)).isEqualTo("外观,尺寸");
        assertThat(row.get(7)).isEqualTo("line1\nline2");
        assertThat(row.get(18)).isEqualTo("a\"b");
    }

    @Test
    void textFieldStartingWithEqualsIsNeutralised() {
        for (String value : List.of("=1+1", "+x", "-x", "@x", "\tx", "\rx")) {
            InspectionRecord record = record();
            record.setRemark(value);

            List<String> row = parse(RecordCsvWriter.write(List.of(record))).get(1);
            assertThat(row.get(18)).as("remark %s", value).isEqualTo("'" + value);
        }

        // 只检查首字符：中间的 = 不是公式起点，不能动。
        InspectionRecord plain = record();
        plain.setRemark("x=1");
        assertThat(parse(RecordCsvWriter.write(List.of(plain))).get(1).get(18)).isEqualTo("x=1");
    }

    @Test
    void negativeNumbersAreNotNeutralised() {
        InspectionRecord record = record();
        record.setMeasuredValue(new BigDecimal("-5"));
        record.setStdLower(new BigDecimal("-5.0000"));

        List<String> row = parse(RecordCsvWriter.write(List.of(record))).get(1);
        // 负号是符号不是公式，数值列绝不加 ' 前缀。
        assertThat(row.get(8)).isEqualTo("-5");
        assertThat(row.get(10)).isEqualTo("-5.0000");

        // 用 toPlainString 而不是 toString：BigDecimal.toString 会输出科学计数法 1E+3。
        InspectionRecord scientific = record();
        scientific.setMeasuredValue(new BigDecimal("1E+3"));
        assertThat(parse(RecordCsvWriter.write(List.of(scientific))).get(1).get(8)).isEqualTo("1000");
    }
}
