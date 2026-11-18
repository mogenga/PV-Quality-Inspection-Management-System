package com.pvqims.inspection;

import java.time.LocalDate;

import com.pvqims.common.api.ApiResult;
import com.pvqims.common.api.PageResult;
import com.pvqims.inspection.dto.DefectDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 缺陷接口（FR-11）。列表对全部已登录角色开放，只读，故不加角色校验。
 */
@RestController
@RequestMapping("/api/defects")
public class DefectController {

    private final DefectService defectService;

    public DefectController(DefectService defectService) {
        this.defectService = defectService;
    }

    @GetMapping
    public ApiResult<PageResult<DefectDetail>> defects(
        @RequestParam(required = false) String grade,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String processName,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResult.ok(defectService.page(grade, status, processName, from, to, page, pageSize));
    }
}
