package com.pvqims.masterdata;

import com.pvqims.common.api.ApiResult;
import com.pvqims.common.api.PageResult;
import com.pvqims.masterdata.dto.BatchSummary;
import com.pvqims.masterdata.dto.CreateBatchRequest;
import com.pvqims.masterdata.dto.UpdateBatchRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 批次管理接口。全部角色可查询，仅工程师可写（SRS 4.8、FR-04）。
 */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public ApiResult<PageResult<BatchSummary>> page(@RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResult.ok(batchService.page(projectId, keyword, status, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<BatchSummary> create(@Valid @RequestBody CreateBatchRequest request) {
        return ApiResult.ok(batchService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<BatchSummary> update(@PathVariable Long id, @Valid @RequestBody UpdateBatchRequest request) {
        return ApiResult.ok(batchService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        batchService.delete(id);
        return ApiResult.ok();
    }
}
