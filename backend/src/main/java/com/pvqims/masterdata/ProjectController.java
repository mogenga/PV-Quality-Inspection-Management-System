package com.pvqims.masterdata;

import com.pvqims.common.api.ApiResult;
import com.pvqims.common.api.PageResult;
import com.pvqims.masterdata.dto.CreateProjectRequest;
import com.pvqims.masterdata.dto.ProjectSummary;
import com.pvqims.masterdata.dto.UpdateProjectRequest;
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
 * 项目管理接口。全部角色可查询，仅工程师可写（SRS 4.8、FR-03）。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResult<PageResult<ProjectSummary>> page(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResult.ok(projectService.page(keyword, status, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<ProjectSummary> create(@Valid @RequestBody CreateProjectRequest request) {
        return ApiResult.ok(projectService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<ProjectSummary> update(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequest request) {
        return ApiResult.ok(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ENGINEER')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResult.ok();
    }
}
