package com.pvqims.inspection;

import java.time.LocalDate;
import java.util.List;

import com.pvqims.common.api.ApiResult;
import com.pvqims.common.api.PageResult;
import com.pvqims.inspection.dto.CreateRecordRequest;
import com.pvqims.inspection.dto.InspectionRecordSummary;
import com.pvqims.inspection.dto.JudgeRequest;
import com.pvqims.inspection.dto.JudgeResult;
import com.pvqims.inspection.dto.StandardTask;
import com.pvqims.inspection.dto.VoidRecordRequest;
import com.pvqims.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校验执行接口（FR-07、FR-08、FR-09、FR-10）。全部角色可查待检项与校验记录；录入仅工程师与检验员可用，
 * 管理员不可录入；作废仅管理员可用（SRS 4.8）。
 */
@RestController
@RequestMapping("/api/inspection")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping("/records")
    public ApiResult<PageResult<InspectionRecordSummary>> records(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Long batchId,
        @RequestParam(required = false) String scene,
        @RequestParam(required = false) String processName,
        @RequestParam(required = false) String result,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "false") boolean includeVoid,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResult.ok(inspectionService.pageRecords(projectId, batchId, scene, processName, result,
            from, to, includeVoid, page, pageSize));
    }

    @GetMapping("/tasks")
    public ApiResult<List<StandardTask>> tasks(@RequestParam(required = false) Long projectId,
                                               @RequestParam Long batchId,
                                               @RequestParam(required = false) String scene,
                                               @RequestParam(required = false) String processName) {
        return ApiResult.ok(inspectionService.tasks(projectId, batchId, scene, processName));
    }

    @PostMapping("/judge")
    @PreAuthorize("hasAnyRole('ENGINEER','INSPECTOR')")
    public ApiResult<JudgeResult> judge(@Valid @RequestBody JudgeRequest request) {
        return ApiResult.ok(new JudgeResult(inspectionService.judge(request)));
    }

    @PostMapping("/records")
    @PreAuthorize("hasAnyRole('ENGINEER','INSPECTOR')")
    public ApiResult<InspectionRecordSummary> createRecord(@Valid @RequestBody CreateRecordRequest request,
                                                           @AuthenticationPrincipal LoginUser user) {
        return ApiResult.ok(inspectionService.createRecord(request, user));
    }

    @PostMapping("/records/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<InspectionRecordSummary> voidRecord(@PathVariable Long id,
                                                         @Valid @RequestBody VoidRecordRequest request) {
        return ApiResult.ok(inspectionService.voidRecord(id, request.reason()));
    }
}
