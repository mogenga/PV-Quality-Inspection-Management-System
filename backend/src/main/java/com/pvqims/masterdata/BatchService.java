package com.pvqims.masterdata;

import java.time.LocalDate;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pvqims.common.api.PageResult;
import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.masterdata.dto.BatchSummary;
import com.pvqims.masterdata.dto.CreateBatchRequest;
import com.pvqims.masterdata.dto.UpdateBatchRequest;
import com.pvqims.masterdata.entity.Batch;
import com.pvqims.masterdata.entity.Project;
import com.pvqims.masterdata.mapper.BatchMapper;
import com.pvqims.masterdata.mapper.ProjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 批次管理（FR-04）。批号项目内唯一；关闭项目下的批次只读（BR-10），有记录的批次不可删除（BR-09）。
 */
@Service
public class BatchService {

    private final BatchMapper batchMapper;
    private final ProjectMapper projectMapper;

    public BatchService(BatchMapper batchMapper, ProjectMapper projectMapper) {
        this.batchMapper = batchMapper;
        this.projectMapper = projectMapper;
    }

    public PageResult<BatchSummary> page(Long projectId, String keyword, String status, long page, long pageSize) {
        LambdaQueryWrapper<Batch> query = new LambdaQueryWrapper<Batch>()
            .eq(projectId != null, Batch::getProjectId, projectId)
            .and(StringUtils.hasText(keyword), w -> w
                .like(Batch::getBatchNo, keyword).or().like(Batch::getRemark, keyword))
            .eq(StringUtils.hasText(status), Batch::getStatus, status)
            .orderByAsc(Batch::getId);
        Page<Batch> result = batchMapper.selectPage(new Page<>(page, pageSize), query);
        List<BatchSummary> records = result.getRecords().stream().map(BatchSummary::from).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public BatchSummary create(CreateBatchRequest request) {
        ensureProjectWritable(request.projectId());
        validateDates(request.startDate(), request.endDate());
        existsByBatchNo(request.projectId(), request.batchNo());
        Batch batch = new Batch();
        batch.setProjectId(request.projectId());
        batch.setBatchNo(request.batchNo());
        batch.setPlannedQty(request.plannedQty());
        batch.setStartDate(request.startDate());
        batch.setEndDate(request.endDate());
        batch.setStatus(request.status());
        batch.setRemark(request.remark());
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException e) {
            // 并发插入同项目同批号时唯一索引兜底。
            throw new BizException(ErrorCode.BATCH_NO_EXISTS);
        }
        return BatchSummary.from(batch);
    }

    public BatchSummary update(Long id, UpdateBatchRequest request) {
        Batch existing = getExisting(id);
        ensureProjectWritable(existing.getProjectId());
        validateDates(request.startDate(), request.endDate());
        Batch update = new Batch();
        update.setId(id);
        update.setBatchNo(request.batchNo());
        update.setPlannedQty(request.plannedQty());
        update.setStartDate(request.startDate());
        update.setEndDate(request.endDate());
        update.setStatus(request.status());
        update.setRemark(request.remark());
        try {
            batchMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            // 改成同项目内已有批号，唯一索引兜底（含并发）。
            throw new BizException(ErrorCode.BATCH_NO_EXISTS);
        }
        return BatchSummary.from(batchMapper.selectById(id));
    }

    public void delete(Long id) {
        Batch existing = getExisting(id);
        ensureProjectWritable(existing.getProjectId());
        try {
            batchMapper.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            // 唯一引用 batch 的外键是 inspection_record.batch_id（RESTRICT），由数据库兜底。
            throw new BizException(ErrorCode.BATCH_HAS_RECORDS);
        }
    }

    private Batch getExisting(Long id) {
        Batch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return batch;
    }

    /**
     * 项目不存在 → NOT_FOUND；已关闭 → PROJECT_CLOSED（BR-10）。
     */
    private void ensureProjectWritable(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if ("CLOSED".equals(project.getStatus())) {
            throw new BizException(ErrorCode.PROJECT_CLOSED);
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
    }

    /**
     * 批号唯一索引使用 utf8mb4_0900_ai_ci，仅大小写不同也算重复。
     */
    private void existsByBatchNo(Long projectId, String batchNo) {
        if (batchMapper.exists(new LambdaQueryWrapper<Batch>()
            .eq(Batch::getProjectId, projectId).eq(Batch::getBatchNo, batchNo))) {
            throw new BizException(ErrorCode.BATCH_NO_EXISTS);
        }
    }
}
