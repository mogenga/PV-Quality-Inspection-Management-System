package com.pvqims.export;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.inspection.InspectionService;
import com.pvqims.inspection.entity.InspectionRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校验记录导出接口（FR-15）。仅工程师可用（SRS 4.8、附录 B）。
 * 筛选参数与 /api/inspection/records 一致，但无分页参数：超上限时明确拒绝而不是静默截断。
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final InspectionService inspectionService;
    private final int maxRows;

    public ExportController(InspectionService inspectionService,
                            @Value("${pvqims.export.max-rows:10000}") int maxRows) {
        this.inspectionService = inspectionService;
        this.maxRows = maxRows;
    }

    @GetMapping("/records.csv")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<byte[]> exportRecords(@RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) Long batchId,
                                                @RequestParam(required = false) String scene,
                                                @RequestParam(required = false) String processName,
                                                @RequestParam(required = false) String result,
                                                @RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to) {
        List<InspectionRecord> records = inspectionService.listForExport(projectId, batchId, scene,
            processName, result, from, to, maxRows);
        if (records.size() > maxRows) {
            throw new BizException(ErrorCode.EXPORT_TOO_LARGE,
                "导出数据超过 %d 条上限，请缩小筛选范围".formatted(maxRows));
        }
        String filename = "records-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(RecordCsvWriter.write(records).getBytes(StandardCharsets.UTF_8));
    }
}
