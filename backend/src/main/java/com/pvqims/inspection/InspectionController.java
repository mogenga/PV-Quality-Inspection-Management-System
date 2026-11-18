package com.pvqims.inspection;

import java.util.List;

import com.pvqims.common.api.ApiResult;
import com.pvqims.inspection.dto.CreateRecordRequest;
import com.pvqims.inspection.dto.InspectionRecordSummary;
import com.pvqims.inspection.dto.JudgeRequest;
import com.pvqims.inspection.dto.JudgeResult;
import com.pvqims.inspection.dto.StandardTask;
import com.pvqims.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校验执行接口（FR-07、FR-08）。全部角色可查待检项；录入仅工程师与检验员可用，管理员不可录入（SRS 4.8）。
 */
@RestController
@RequestMapping("/api/inspection")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
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
                                                           Authentication authentication) {
        return ApiResult.ok(inspectionService.createRecord(request, (LoginUser) authentication.getPrincipal()));
    }
}
