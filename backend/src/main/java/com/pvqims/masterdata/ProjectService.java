package com.pvqims.masterdata;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pvqims.common.api.PageResult;
import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.masterdata.dto.CreateProjectRequest;
import com.pvqims.masterdata.dto.ProjectSummary;
import com.pvqims.masterdata.dto.UpdateProjectRequest;
import com.pvqims.masterdata.entity.Project;
import com.pvqims.masterdata.mapper.ProjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 项目管理（FR-03）。项目编号全系统唯一；仍有批次或标准的项目不允许删除。
 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;

    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    public PageResult<ProjectSummary> page(String keyword, String status, long page, long pageSize) {
        LambdaQueryWrapper<Project> query = new LambdaQueryWrapper<Project>()
            .and(StringUtils.hasText(keyword), w -> w
                .like(Project::getProjectCode, keyword).or().like(Project::getProjectName, keyword))
            .eq(StringUtils.hasText(status), Project::getStatus, status)
            .orderByAsc(Project::getId);
        Page<Project> result = projectMapper.selectPage(new Page<>(page, pageSize), query);
        List<ProjectSummary> records = result.getRecords().stream().map(ProjectSummary::from).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ProjectSummary create(CreateProjectRequest request) {
        existsByCode(request.projectCode());
        Project project = new Project();
        project.setProjectCode(request.projectCode());
        project.setProjectName(request.projectName());
        project.setProductModel(request.productModel());
        project.setCustomerName(request.customerName());
        project.setManufacturerName(request.manufacturerName());
        project.setLocation(request.location());
        project.setStatus(request.status());
        project.setRemark(request.remark());
        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException e) {
            // 并发插入同编号项目时唯一索引兜底。
            throw new BizException(ErrorCode.PROJECT_CODE_EXISTS);
        }
        return ProjectSummary.from(project);
    }

    public ProjectSummary update(Long id, UpdateProjectRequest request) {
        getExisting(id);
        Project update = new Project();
        update.setId(id);
        update.setProjectCode(request.projectCode());
        update.setProjectName(request.projectName());
        update.setProductModel(request.productModel());
        update.setCustomerName(request.customerName());
        update.setManufacturerName(request.manufacturerName());
        update.setLocation(request.location());
        update.setStatus(request.status());
        update.setRemark(request.remark());
        try {
            projectMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            // 编号改成已有项目的值，唯一索引兜底（含并发）。
            throw new BizException(ErrorCode.PROJECT_CODE_EXISTS);
        }
        return ProjectSummary.from(projectMapper.selectById(id));
    }

    public void delete(Long id) {
        getExisting(id);
        try {
            projectMapper.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            // fk_batch_project、fk_std_project 均为 RESTRICT，由数据库兜底。
            throw new BizException(ErrorCode.PROJECT_HAS_DEPENDENTS);
        }
    }

    private Project getExisting(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return project;
    }

    /**
     * 项目编号唯一索引使用 utf8mb4_0900_ai_ci，仅大小写不同也算重复。
     */
    private void existsByCode(String projectCode) {
        if (projectMapper.exists(new LambdaQueryWrapper<Project>().eq(Project::getProjectCode, projectCode))) {
            throw new BizException(ErrorCode.PROJECT_CODE_EXISTS);
        }
    }
}
