package com.pvqims.standard;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pvqims.common.api.PageResult;
import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.inspection.JudgmentRules;
import com.pvqims.masterdata.entity.Project;
import com.pvqims.masterdata.mapper.ProjectMapper;
import com.pvqims.standard.dto.CreateStandardRequest;
import com.pvqims.standard.dto.StandardSummary;
import com.pvqims.standard.dto.UpdateStandardRequest;
import com.pvqims.standard.entity.InspectionStandard;
import com.pvqims.standard.mapper.InspectionStandardMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 校验标准配置（FR-05、FR-06）。配置期校验按判定方式检查必填数值列，并把无关数值列清空后落库，
 * 保证后续判定引擎拿不到过期上限。关闭项目下的标准只读（BR-10）。
 */
@Service
public class StandardService {

    private final InspectionStandardMapper standardMapper;
    private final ProjectMapper projectMapper;
    private final JudgmentRules judgmentRules;

    public StandardService(InspectionStandardMapper standardMapper, ProjectMapper projectMapper,
                           JudgmentRules judgmentRules) {
        this.standardMapper = standardMapper;
        this.projectMapper = projectMapper;
        this.judgmentRules = judgmentRules;
    }

    public PageResult<StandardSummary> page(Long projectId, String scene, String keyword, Boolean enabled,
                                            long page, long pageSize) {
        // 关键字 OR 必须分组，否则会绕过 scene/enabled 过滤；排序按 sortOrder 而非 id（FR-06）。
        LambdaQueryWrapper<InspectionStandard> query = new LambdaQueryWrapper<InspectionStandard>()
            .eq(projectId != null, InspectionStandard::getProjectId, projectId)
            .and(StringUtils.hasText(keyword), w -> w
                .like(InspectionStandard::getItemCode, keyword).or().like(InspectionStandard::getItemName, keyword))
            .eq(StringUtils.hasText(scene), InspectionStandard::getScene, scene)
            .eq(enabled != null, InspectionStandard::getIsEnabled, enabled)
            .orderByAsc(InspectionStandard::getSortOrder)
            .orderByAsc(InspectionStandard::getId);
        Page<InspectionStandard> result = standardMapper.selectPage(new Page<>(page, pageSize), query);
        List<StandardSummary> records = result.getRecords().stream().map(StandardSummary::from).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public StandardSummary create(CreateStandardRequest request) {
        ensureProjectWritable(request.projectId());
        validate(request.judgeType(), request.valueType(), request.lowerLimit(), request.upperLimit(),
            request.targetValue(), request.tolerance());
        JudgmentRules.Numeric numeric = judgmentRules.normalize(request.judgeType(), request.lowerLimit(),
            request.upperLimit(), request.targetValue(), request.tolerance());
        existsByItemCode(request.projectId(), request.itemCode());
        InspectionStandard standard = new InspectionStandard();
        standard.setProjectId(request.projectId());
        standard.setScene(request.scene());
        standard.setProcessName(request.processName());
        standard.setItemCode(request.itemCode());
        standard.setItemName(request.itemName());
        standard.setUnit(request.unit());
        standard.setValueType(request.valueType());
        standard.setJudgeType(request.judgeType());
        standard.setLowerLimit(numeric.lower());
        standard.setUpperLimit(numeric.upper());
        standard.setTargetValue(numeric.target());
        standard.setTolerance(numeric.tolerance());
        standard.setSamplingNote(request.samplingNote());
        standard.setIsEnabled(request.isEnabled());
        standard.setSortOrder(request.sortOrder());
        standard.setRemark(request.remark());
        try {
            standardMapper.insert(standard);
        } catch (DuplicateKeyException e) {
            // 并发插入同项目同编码时唯一索引兜底。
            throw new BizException(ErrorCode.STANDARD_ITEM_EXISTS);
        }
        return StandardSummary.from(standard);
    }

    public StandardSummary update(Long id, UpdateStandardRequest request) {
        InspectionStandard existing = getExisting(id);
        ensureProjectWritable(existing.getProjectId());
        validate(request.judgeType(), request.valueType(), request.lowerLimit(), request.upperLimit(),
            request.targetValue(), request.tolerance());
        JudgmentRules.Numeric numeric = judgmentRules.normalize(request.judgeType(), request.lowerLimit(),
            request.upperLimit(), request.targetValue(), request.tolerance());
        InspectionStandard update = new InspectionStandard();
        update.setId(id);
        update.setScene(request.scene());
        update.setProcessName(request.processName());
        update.setItemCode(request.itemCode());
        update.setItemName(request.itemName());
        update.setUnit(request.unit());
        update.setValueType(request.valueType());
        update.setJudgeType(request.judgeType());
        update.setLowerLimit(numeric.lower());
        update.setUpperLimit(numeric.upper());
        update.setTargetValue(numeric.target());
        update.setTolerance(numeric.tolerance());
        update.setSamplingNote(request.samplingNote());
        update.setIsEnabled(request.isEnabled());
        update.setSortOrder(request.sortOrder());
        update.setRemark(request.remark());
        try {
            standardMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            // 编码改成项目内已有的值，唯一索引兜底（含并发）。
            throw new BizException(ErrorCode.STANDARD_ITEM_EXISTS);
        }
        return StandardSummary.from(standardMapper.selectById(id));
    }

    public StandardSummary setEnabled(Long id, Boolean isEnabled) {
        InspectionStandard existing = getExisting(id);
        ensureProjectWritable(existing.getProjectId());
        // 只 SET is_enabled：实体上七个可空列是 ALWAYS 策略，走 updateById 会把它们一并写成 NULL。
        standardMapper.update(new LambdaUpdateWrapper<InspectionStandard>()
            .eq(InspectionStandard::getId, id)
            .set(InspectionStandard::getIsEnabled, isEnabled));
        return StandardSummary.from(standardMapper.selectById(id));
    }

    private InspectionStandard getExisting(Long id) {
        InspectionStandard standard = standardMapper.selectById(id);
        if (standard == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return standard;
    }

    /**
     * 项目不存在 → NOT_FOUND；已关闭 → PROJECT_CLOSED，文案换成本模块的（BR-10）。
     */
    private void ensureProjectWritable(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if ("CLOSED".equals(project.getStatus())) {
            throw new BizException(ErrorCode.PROJECT_CLOSED, "项目已关闭，标准不可修改");
        }
    }

    /**
     * 唯一索引 uk_std_project_item 使用 utf8mb4_0900_ai_ci，仅大小写不同也算重复。
     */
    private void existsByItemCode(Long projectId, String itemCode) {
        if (standardMapper.exists(new LambdaQueryWrapper<InspectionStandard>()
            .eq(InspectionStandard::getProjectId, projectId).eq(InspectionStandard::getItemCode, itemCode))) {
            throw new BizException(ErrorCode.STANDARD_ITEM_EXISTS);
        }
    }

    /**
     * 配置期校验（SRS 4.4 约束）：必须在规整前对原始输入执行，否则无关列里的非法值会被静默清掉。
     * 数值列规则委托 {@link JudgmentRules}；值类型一致性是配置期规则，留在本类。
     */
    private void validate(String judgeType, String valueType, BigDecimal lower, BigDecimal upper,
                          BigDecimal target, BigDecimal tolerance) {
        judgmentRules.validateNumeric(judgeType, lower, upper, target, tolerance);
        if ("PASS_FAIL".equals(judgeType)) {
            if (!"TEXT".equals(valueType)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "人工判定的值类型必须是 TEXT");
            }
        } else if (!"NUMERIC".equals(valueType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "数值型判定方式的值类型必须是 NUMERIC");
        }
    }
}
