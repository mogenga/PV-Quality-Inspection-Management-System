package com.pvqims.inspection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pvqims.common.api.PageResult;
import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.inspection.dto.CreateRecordRequest;
import com.pvqims.inspection.dto.InspectionRecordSummary;
import com.pvqims.inspection.dto.JudgeRequest;
import com.pvqims.inspection.dto.StandardTask;
import com.pvqims.inspection.entity.Defect;
import com.pvqims.inspection.entity.InspectionRecord;
import com.pvqims.inspection.mapper.DefectMapper;
import com.pvqims.inspection.mapper.InspectionRecordMapper;
import com.pvqims.masterdata.entity.Batch;
import com.pvqims.masterdata.entity.Project;
import com.pvqims.masterdata.mapper.BatchMapper;
import com.pvqims.masterdata.mapper.ProjectMapper;
import com.pvqims.security.LoginUser;
import com.pvqims.standard.entity.InspectionStandard;
import com.pvqims.standard.mapper.InspectionStandardMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 校验执行（FR-07、FR-08）：待检项来自启用的标准，判定只在服务端执行并复制标准快照（BR-01、BR-02），
 * FAIL 记录在同一事务内生成 1:1 的缺陷单（BR-04）。
 */
@Service
public class InspectionService {

    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);

    private final InspectionStandardMapper standardMapper;
    private final BatchMapper batchMapper;
    private final ProjectMapper projectMapper;
    private final InspectionRecordMapper recordMapper;
    private final DefectMapper defectMapper;
    private final JudgmentRules judgmentRules;

    public InspectionService(InspectionStandardMapper standardMapper, BatchMapper batchMapper,
                             ProjectMapper projectMapper, InspectionRecordMapper recordMapper,
                             DefectMapper defectMapper, JudgmentRules judgmentRules) {
        this.standardMapper = standardMapper;
        this.batchMapper = batchMapper;
        this.projectMapper = projectMapper;
        this.recordMapper = recordMapper;
        this.defectMapper = defectMapper;
        this.judgmentRules = judgmentRules;
    }

    public List<StandardTask> tasks(Long projectId, Long batchId, String scene, String processName) {
        Batch batch = getBatch(batchId);
        if (projectId != null && !projectId.equals(batch.getProjectId())) {
            throw new BizException(ErrorCode.RECORD_PROJECT_MISMATCH);
        }
        List<InspectionStandard> standards = standardMapper.selectList(
            new LambdaQueryWrapper<InspectionStandard>()
                .eq(InspectionStandard::getProjectId, batch.getProjectId())
                .eq(InspectionStandard::getIsEnabled, true)
                .eq(StringUtils.hasText(scene), InspectionStandard::getScene, scene)
                .eq(StringUtils.hasText(processName), InspectionStandard::getProcessName, processName)
                .orderByAsc(InspectionStandard::getSortOrder)
                .orderByAsc(InspectionStandard::getId));

        Map<Long, InspectionRecord> lastByStandard = lastRecordsByStandard(batchId);
        return standards.stream().map(standard -> {
            InspectionRecord last = lastByStandard.get(standard.getId());
            return new StandardTask(standard.getId(), standard.getScene(), standard.getProcessName(),
                standard.getItemCode(), standard.getItemName(),
                standard.getUnit(), standard.getValueType(), standard.getJudgeType(), standard.getLowerLimit(),
                standard.getUpperLimit(), standard.getTargetValue(), standard.getTolerance(),
                standard.getSortOrder(),
                last == null ? null : last.getMeasuredValue(),
                last == null ? null : last.getResult(),
                last == null ? null : last.getInspectTime());
        }).toList();
    }

    /**
     * 判定预览（FR-08）：读标准求值，不落库。
     */
    public String judge(JudgeRequest request) {
        InspectionStandard standard = getEnabledStandard(request.standardId());
        return judgmentRules.evaluate(standard.getJudgeType(), request.measuredValue(), request.textValue(),
            numericOf(standard));
    }

    /**
     * 提交校验记录（FR-08）：服务端重新判定（BR-01），同事务内复制标准快照，FAIL 时生成缺陷单（BR-04）。
     */
    @Transactional
    public InspectionRecordSummary createRecord(CreateRecordRequest request, LoginUser inspector) {
        Batch batch = getBatch(request.batchId());
        InspectionStandard standard = getEnabledStandard(request.standardId());
        if (!standard.getProjectId().equals(batch.getProjectId())) {
            throw new BizException(ErrorCode.RECORD_PROJECT_MISMATCH);
        }
        Project project = projectMapper.selectById(batch.getProjectId());
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if ("CLOSED".equals(project.getStatus())) {
            throw new BizException(ErrorCode.PROJECT_CLOSED, "项目已关闭，不能录入校验记录");
        }
        String result = judgmentRules.evaluate(standard.getJudgeType(), request.measuredValue(),
            request.textValue(), numericOf(standard));
        if ("FAIL".equals(result)
            && (!StringUtils.hasText(request.defectType()) || !StringUtils.hasText(request.defectGrade()))) {
            throw new BizException(ErrorCode.DEFECT_INFO_REQUIRED);
        }

        InspectionRecord record = new InspectionRecord();
        record.setProjectId(batch.getProjectId());
        record.setBatchId(batch.getId());
        record.setStandardId(standard.getId());
        record.setScene(standard.getScene());
        record.setProcessName(standard.getProcessName());
        record.setItemName(standard.getItemName());
        record.setSerialNo(request.serialNo());
        record.setInspectorId(inspector.id());
        record.setInspectTime(LocalDateTime.now());
        record.setMeasuredValue(request.measuredValue());
        record.setTextValue(request.textValue());
        record.setResult(result);
        record.setStdLower(standard.getLowerLimit());
        record.setStdUpper(standard.getUpperLimit());
        record.setStdTarget(standard.getTargetValue());
        record.setStdTolerance(standard.getTolerance());
        record.setStdUnit(standard.getUnit());
        record.setIsVoid(false);
        record.setRemark(request.remark());
        recordMapper.insert(record);

        if ("FAIL".equals(result)) {
            Defect defect = new Defect();
            defect.setRecordId(record.getId());
            defect.setDefectType(request.defectType());
            defect.setDefectGrade(request.defectGrade());
            defect.setDefectStatus("OPEN");
            defectMapper.insert(defect);
        }
        return InspectionRecordSummary.from(record);
    }

    /**
     * 记录作废（FR-10）：只写 is_void 与 void_reason，记录保留（BR-08）。仅管理员可操作（SRS 4.8）。
     */
    @Transactional
    public InspectionRecordSummary voidRecord(Long id, String reason) {
        InspectionRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (Boolean.TRUE.equals(record.getIsVoid())) {
            throw new BizException(ErrorCode.RECORD_ALREADY_VOID);
        }
        // 只 SET 两列：实体可空列是 ALWAYS 策略，走 updateById 会把它们一并写成 NULL。
        recordMapper.update(new LambdaUpdateWrapper<InspectionRecord>()
            .eq(InspectionRecord::getId, id)
            .set(InspectionRecord::getIsVoid, true)
            .set(InspectionRecord::getVoidReason, reason));
        log.info("记录作废 recordId={} reason={}", id, reason);
        return InspectionRecordSummary.from(recordMapper.selectById(id));
    }

    /**
     * 记录查询与筛选（FR-09）。默认排除作废记录，includeVoid 为真时才一并返回（BR-08）。
     * to 取次日零点（闭区间 ge/le），避免用 23:59:59 漏掉最后一秒内的记录。
     */
    public PageResult<InspectionRecordSummary> pageRecords(Long projectId, Long batchId, String scene,
                                                           String processName, String result, LocalDate from,
                                                           LocalDate to, boolean includeVoid, long page,
                                                           long pageSize) {
        LambdaQueryWrapper<InspectionRecord> query = new LambdaQueryWrapper<InspectionRecord>()
            .eq(projectId != null, InspectionRecord::getProjectId, projectId)
            .eq(batchId != null, InspectionRecord::getBatchId, batchId)
            .eq(StringUtils.hasText(scene), InspectionRecord::getScene, scene)
            .eq(StringUtils.hasText(processName), InspectionRecord::getProcessName, processName)
            .eq(StringUtils.hasText(result), InspectionRecord::getResult, result)
            .eq(!includeVoid, InspectionRecord::getIsVoid, false)
            .ge(from != null, InspectionRecord::getInspectTime, from == null ? null : from.atStartOfDay())
            .le(to != null, InspectionRecord::getInspectTime,
                to == null ? null : to.plusDays(1).atStartOfDay())
            .orderByDesc(InspectionRecord::getInspectTime)
            .orderByDesc(InspectionRecord::getId);
        Page<InspectionRecord> records = recordMapper.selectPage(new Page<>(page, pageSize), query);
        List<InspectionRecordSummary> summaries = records.getRecords().stream()
            .map(InspectionRecordSummary::from).toList();
        return new PageResult<>(summaries, records.getTotal(), records.getCurrent(), records.getSize());
    }

    /**
     * 标准行里的数值列在配置期已规整，直接作为快照与判定输入。
     */
    private JudgmentRules.Numeric numericOf(InspectionStandard standard) {
        return new JudgmentRules.Numeric(standard.getLowerLimit(), standard.getUpperLimit(),
            standard.getTargetValue(), standard.getTolerance());
    }

    private Batch getBatch(Long batchId) {
        Batch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return batch;
    }

    private InspectionStandard getEnabledStandard(Long standardId) {
        InspectionStandard standard = standardMapper.selectById(standardId);
        if (standard == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(standard.getIsEnabled())) {
            throw new BizException(ErrorCode.STANDARD_DISABLED);
        }
        return standard;
    }

    /**
     * 一次查出该批次全部有效记录，按标准分组取 inspectTime 最大的一条，避免逐标准查询。
     * inspect_time 是秒精度，同秒记录以自增 id 大者为新。
     */
    private Map<Long, InspectionRecord> lastRecordsByStandard(Long batchId) {
        List<InspectionRecord> records = recordMapper.selectList(
            new LambdaQueryWrapper<InspectionRecord>()
                .select(InspectionRecord::getId, InspectionRecord::getStandardId,
                    InspectionRecord::getMeasuredValue, InspectionRecord::getResult,
                    InspectionRecord::getInspectTime)
                .eq(InspectionRecord::getBatchId, batchId)
                .eq(InspectionRecord::getIsVoid, false));
        Map<Long, InspectionRecord> lastByStandard = new HashMap<>();
        for (InspectionRecord record : records) {
            lastByStandard.merge(record.getStandardId(), record,
                (a, b) -> b.getInspectTime().isAfter(a.getInspectTime())
                    || (b.getInspectTime().isEqual(a.getInspectTime()) && b.getId() > a.getId()) ? b : a);
        }
        return lastByStandard;
    }
}
