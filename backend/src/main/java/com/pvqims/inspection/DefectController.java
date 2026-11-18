package com.pvqims.inspection;

import java.time.LocalDate;

import com.pvqims.common.api.ApiResult;
import com.pvqims.common.api.PageResult;
import com.pvqims.inspection.dto.DefectActionRequest;
import com.pvqims.inspection.dto.DefectDetail;
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
 * 缺陷接口（FR-11、FR-12）。列表对全部已登录角色开放，只读，故不加角色校验；
 * 状态流转仅工程师可操作，检验员与管理员只读（SRS 4.8）。
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

    @PostMapping("/{id}/process")
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<Void> process(@PathVariable Long id, @Valid @RequestBody DefectActionRequest request,
                                   @AuthenticationPrincipal LoginUser user) {
        defectService.process(id, request.note(), user);
        return ApiResult.ok(null);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<Void> verify(@PathVariable Long id, @Valid @RequestBody DefectActionRequest request,
                                  @AuthenticationPrincipal LoginUser user) {
        defectService.verify(id, request.note(), user);
        return ApiResult.ok(null);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<Void> close(@PathVariable Long id, @Valid @RequestBody DefectActionRequest request,
                                 @AuthenticationPrincipal LoginUser user) {
        defectService.close(id, request.note(), user);
        return ApiResult.ok(null);
    }
}
