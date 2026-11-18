package com.pvqims.inspection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pvqims.common.api.PageResult;
import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.inspection.dto.DefectDetail;
import com.pvqims.inspection.entity.Defect;
import com.pvqims.inspection.mapper.DefectMapper;
import com.pvqims.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 缺陷（FR-11、FR-12）：只读列表按等级、状态、工序、时间段筛选；
 * 状态只能 OPEN → PROCESSING → VERIFIED → CLOSED 顺序流转，VERIFIED 可退回 PROCESSING（BR-05）。
 * 记录作废后其缺陷不在列表中（BR-08），缺陷行保留。
 */
@Service
public class DefectService {

    private static final Logger log = LoggerFactory.getLogger(DefectService.class);

    private static final Map<String, String> STATUS_LABELS = Map.of(
        "OPEN", "待处理", "PROCESSING", "处理中", "VERIFIED", "待验证", "CLOSED", "已关闭");

    private final DefectMapper defectMapper;

    public DefectService(DefectMapper defectMapper) {
        this.defectMapper = defectMapper;
    }

    /**
     * 日期闭区间：to 取次日零点，避免用 23:59:59 漏掉最后一秒内的缺陷（与记录查询同一口径）。
     */
    public PageResult<DefectDetail> page(String grade, String status, String processName,
                                         LocalDate from, LocalDate to, long page, long pageSize) {
        IPage<DefectDetail> rows = defectMapper.selectDetailPage(new Page<>(page, pageSize), grade, status,
            processName, from == null ? null : from.atStartOfDay(),
            to == null ? null : to.plusDays(1).atStartOfDay());
        return new PageResult<>(rows.getRecords(), rows.getTotal(), rows.getCurrent(), rows.getSize());
    }

    /**
     * 认领并开始处理（FR-12）：OPEN → PROCESSING；VERIFIED → PROCESSING 是验证不通过退回，必须给原因。
     */
    public void process(Long id, String note, LoginUser operator) {
        transition(id, "开始处理", Set.of("OPEN", "VERIFIED"), "PROCESSING", note, operator);
    }

    /**
     * 提交整改等待验证（FR-12）：PROCESSING → VERIFIED。
     */
    public void verify(Long id, String note, LoginUser operator) {
        transition(id, "验证", Set.of("PROCESSING"), "VERIFIED", note, operator);
    }

    /**
     * 验证通过关闭缺陷（FR-12）：VERIFIED → CLOSED，SRS 4.6 要求必须填写处理说明。
     */
    public void close(Long id, String note, LoginUser operator) {
        transition(id, "关闭", Set.of("VERIFIED"), "CLOSED", note, operator);
    }

    /**
     * 流转的唯一样板：起始状态不在 allowedFrom 内即 400，其余字段一次 SET 完。
     * 三个公开方法各自只声明自己的 allowedFrom —— 跳级因此在结构上不可能，而不是靠 if 链拦。
     * 说明必填的判定写在方法内：关闭（目标 CLOSED）与验证不通过（VERIFIED → PROCESSING）
     * 都必须给原因，这不是三个调用点各记一遍的约定。
     */
    private void transition(Long id, String action, Set<String> allowedFrom, String target,
                            String note, LoginUser operator) {
        Defect defect = defectMapper.selectById(id);
        if (defect == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String current = defect.getDefectStatus();
        if (!allowedFrom.contains(current)) {
            throw new BizException(ErrorCode.DEFECT_STATUS_ILLEGAL,
                "缺陷当前状态为「%s」，不能%s".formatted(STATUS_LABELS.get(current), action));
        }
        boolean noteRequired = "CLOSED".equals(target) || ("PROCESSING".equals(target) && "VERIFIED".equals(current));
        if (noteRequired && !StringUtils.hasText(note)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写处理说明");
        }
        // 实体可空列是 ALWAYS 策略，走 updateById 会把它们一并写成 NULL；这里精确 SET 要写的列。
        LambdaUpdateWrapper<Defect> update = new LambdaUpdateWrapper<Defect>()
            .eq(Defect::getId, id)
            .set(Defect::getDefectStatus, target)
            .set(Defect::getHandlerId, operator.id())
            .set(Defect::getResolvedAt, "CLOSED".equals(target) ? LocalDateTime.now() : null);
        // 未填说明时不动 handle_note：单列覆盖写，清空会让上一环节的说明凭空消失。
        if (StringUtils.hasText(note)) {
            update.set(Defect::getHandleNote, note);
        }
        defectMapper.update(update);
        log.info("缺陷流转 defectId={} {} -> {} operator={} note={}",
            id, current, target, operator.username(), note);
    }
}
