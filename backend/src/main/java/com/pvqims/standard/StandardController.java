package com.pvqims.standard;

import com.pvqims.common.api.ApiResult;
import com.pvqims.common.api.PageResult;
import com.pvqims.standard.dto.CreateStandardRequest;
import com.pvqims.standard.dto.SetEnabledRequest;
import com.pvqims.standard.dto.StandardSummary;
import com.pvqims.standard.dto.UpdateStandardRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校验标准配置接口。全部角色可查询，仅工程师可写（SRS 4.8、FR-05/FR-06）。
 */
@RestController
@RequestMapping("/api/standards")
public class StandardController {

    private final StandardService standardService;

    public StandardController(StandardService standardService) {
        this.standardService = standardService;
    }

    @GetMapping
    public ApiResult<PageResult<StandardSummary>> page(@RequestParam(required = false) Long projectId,
                                                       @RequestParam(required = false) String scene,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Boolean enabled,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResult.ok(standardService.page(projectId, scene, keyword, enabled, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<StandardSummary> create(@Valid @RequestBody CreateStandardRequest request) {
        return ApiResult.ok(standardService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<StandardSummary> update(@PathVariable Long id, @Valid @RequestBody UpdateStandardRequest request) {
        return ApiResult.ok(standardService.update(id, request));
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<StandardSummary> setEnabled(@PathVariable Long id, @Valid @RequestBody SetEnabledRequest request) {
        return ApiResult.ok(standardService.setEnabled(id, request.isEnabled()));
    }
}
