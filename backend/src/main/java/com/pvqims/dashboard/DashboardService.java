package com.pvqims.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.pvqims.common.exception.BizException;
import com.pvqims.common.exception.ErrorCode;
import com.pvqims.dashboard.dto.DashboardDtos.BatchPassRate;
import com.pvqims.dashboard.dto.DashboardDtos.ControlChart;
import com.pvqims.dashboard.dto.DashboardDtos.ControlPoint;
import com.pvqims.dashboard.dto.DashboardDtos.DefectDuration;
import com.pvqims.dashboard.dto.DashboardDtos.GradeSceneCount;
import com.pvqims.dashboard.dto.DashboardDtos.InspectorWorkload;
import com.pvqims.dashboard.dto.DashboardDtos.ParetoItem;
import com.pvqims.dashboard.dto.DashboardDtos.ProcessComparison;
import com.pvqims.dashboard.dto.DashboardDtos.Summary;
import com.pvqims.dashboard.dto.DashboardDtos.TrendPoint;
import com.pvqims.standard.entity.InspectionStandard;
import com.pvqims.standard.mapper.InspectionStandardMapper;
import org.springframework.stereotype.Service;

/**
 * 看板（FR-13、FR-14）：全部实时聚合，不做预聚合表（SRS 4.7）。
 * 作废记录不参与任何统计（BR-08、AC-06）；日期筛选与记录查询同一闭区间口径。
 */
@Service
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    private final InspectionStandardMapper standardMapper;

    public DashboardService(DashboardMapper dashboardMapper, InspectionStandardMapper standardMapper) {
        this.dashboardMapper = dashboardMapper;
        this.standardMapper = standardMapper;
    }

    /**
     * 指标卡：记录数与合格数由趋势行求和，未关闭缺陷与平均处理时长另查 defect。
     * 两张表的数字分开查再合并，不拼一条带子查询的 SQL。
     */
    public Summary summary(DashboardQuery query) {
        LocalDateTime from = fromOf(query);
        LocalDateTime to = toOf(query);
        long total = 0;
        long passed = 0;
        for (DashboardMapper.TrendRow row : dashboardMapper.selectTrend(query.projectId(), query.batchId(),
            from, to, query.scene(), query.processName())) {
            total += row.getTotal();
            passed += passedOf(row.getPassed());
        }
        long openDefects = dashboardMapper.countOpenDefects(query.projectId(), query.batchId(), from, to,
            query.scene(), query.processName());
        Double avgHandleHours = dashboardMapper.selectAvgHandleHours(query.projectId(), query.batchId(),
            from, to, query.scene(), query.processName());
        return new Summary(total, passed, rate(passed, total), openDefects, avgHandleHours);
    }

    /**
     * CH-01 每日合格率趋势。
     */
    public List<TrendPoint> passRateTrend(DashboardQuery query) {
        return dashboardMapper.selectTrend(query.projectId(), query.batchId(), fromOf(query), toOf(query),
                query.scene(), query.processName())
            .stream()
            .map(row -> {
                long passed = passedOf(row.getPassed());
                return new TrendPoint(row.getDay(), row.getTotal(), passed, rate(passed, row.getTotal()));
            })
            .toList();
    }

    /**
     * CH-03 工序合格率对比，按合格率升序（SQL 已排序）。
     */
    public List<ProcessComparison> processComparison(DashboardQuery query) {
        return dashboardMapper.selectProcessComparison(query.projectId(), query.batchId(), fromOf(query),
                toOf(query), query.scene(), query.processName())
            .stream()
            .map(row -> {
                long passed = passedOf(row.getPassed());
                return new ProcessComparison(row.getProcessName(), row.getTotal(), passed,
                    rate(passed, row.getTotal()));
            })
            .toList();
    }

    /**
     * CH-02 缺陷帕累托：SQL 已按次数降序，累计百分比在服务层算。
     * 最后一项强制写 100.0——浮点累计到最后可能得 99.9，AC-09 要的是确定值。
     */
    public List<ParetoItem> pareto(DashboardQuery query) {
        List<DashboardMapper.ParetoRow> rows = dashboardMapper.selectPareto(query.projectId(),
            query.batchId(), fromOf(query), toOf(query), query.scene(), query.processName());
        long total = 0;
        for (DashboardMapper.ParetoRow row : rows) {
            total += row.getCnt();
        }
        if (total == 0) {
            return List.of();
        }
        List<ParetoItem> items = new ArrayList<>(rows.size());
        long cumulative = 0;
        for (int i = 0; i < rows.size(); i++) {
            DashboardMapper.ParetoRow row = rows.get(i);
            cumulative += row.getCnt();
            double percent = i == rows.size() - 1
                ? 100.0
                : Math.round(cumulative * 1000.0 / total) / 10.0;
            items.add(new ParetoItem(row.getDefectType(), row.getCnt(), percent));
        }
        return items;
    }

    /**
     * CH-05 缺陷等级构成，按场景 × 等级计数（SQL 已排序）。
     */
    public List<GradeSceneCount> defectGradeByScene(DashboardQuery query) {
        return dashboardMapper.selectDefectGradeByScene(query.projectId(), query.batchId(), fromOf(query),
                toOf(query), query.scene(), query.processName())
            .stream()
            .map(row -> new GradeSceneCount(row.getScene(), row.getDefectGrade(), row.getCnt()))
            .toList();
    }

    /**
     * CH-08 缺陷处理时长：只含已关闭缺陷，平均小时数保留一位小数。
     * avgHours 为 NULL 的等级本就不出现在结果里（SQL 已过滤），无须再判空。
     */
    public List<DefectDuration> defectDuration(DashboardQuery query) {
        return dashboardMapper.selectDefectDuration(query.projectId(), query.batchId(), fromOf(query),
                toOf(query), query.scene(), query.processName())
            .stream()
            .map(row -> new DefectDuration(row.getDefectGrade(),
                Math.round(row.getAvgHours() * 10.0) / 10.0))
            .toList();
    }

    /**
     * CH-04 参数控制图：控制限取自标准本身，不另行计算（SRS 6.3、AC-09）。
     * 取数按时间倒序取最近 30 条，这里反转成升序，画图从左到右是时间正序。
     */
    public ControlChart controlChart(Long standardId) {
        InspectionStandard standard = standardMapper.selectById(standardId);
        if (standard == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        ControlLimits limits = limitsOf(standard);
        List<ControlPoint> points = dashboardMapper.selectControlPoints(standardId).stream()
            .map(row -> new ControlPoint(row.getInspectTime(), row.getMeasuredValue()))
            .toList()
            .reversed();
        return new ControlChart(standard.getItemName(), standard.getUnit(), limits.ucl(), limits.lcl(),
            limits.cl(), points);
    }

    /**
     * CH-07 检验员工作量，SQL 已按次数降序。
     */
    public List<InspectorWorkload> inspectorWorkload(DashboardQuery query) {
        return dashboardMapper.selectInspectorWorkload(query.projectId(), query.batchId(), fromOf(query),
                toOf(query), query.scene(), query.processName())
            .stream()
            .map(row -> new InspectorWorkload(row.getInspectorId(), row.getInspectorName(), row.getCnt()))
            .toList();
    }

    /**
     * CH-06 批次合格率，同时服务 FR-13 的批次汇总表；passRate 规则同指标卡。
     */
    public List<BatchPassRate> batchPassRates(DashboardQuery query) {
        return dashboardMapper.selectBatchPassRates(query.projectId(), query.batchId(), fromOf(query),
                toOf(query), query.scene(), query.processName())
            .stream()
            .map(row -> {
                long passed = passedOf(row.getPassed());
                return new BatchPassRate(row.getBatchId(), row.getBatchNo(), row.getTotal(), passed,
                    rate(passed, row.getTotal()));
            })
            .toList();
    }

    /**
     * 控制限推导（SRS 6.3「控制限取自标准的上下限」）。标准的四个数值列在配置期已按判定方式规整
     * （{@code JudgmentRules.normalize} 把无关列置空），故这里不判空；未知判定方式兜底报参数错误。
     */
    private static ControlLimits limitsOf(InspectionStandard standard) {
        BigDecimal lower = standard.getLowerLimit();
        BigDecimal upper = standard.getUpperLimit();
        BigDecimal target = standard.getTargetValue();
        BigDecimal tolerance = standard.getTolerance();
        return switch (standard.getJudgeType()) {
            case "RANGE" -> new ControlLimits(upper, lower,
                lower.add(upper).divide(BigDecimal.valueOf(2)));
            case "GT" -> new ControlLimits(null, lower, lower);
            case "LT" -> new ControlLimits(upper, null, upper);
            case "STD_TOL" -> new ControlLimits(target.add(tolerance), target.subtract(tolerance), target);
            case "PASS_FAIL" -> throw new BizException(ErrorCode.PARAM_INVALID, "人工判定项没有数值控制图");
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知的判定方式");
        };
    }

    /** 控制图的 UCL/CL/LCL。单侧判定方式没有的那一侧为 null。 */
    private record ControlLimits(BigDecimal ucl, BigDecimal lcl, BigDecimal cl) {
    }

    /** 日期闭区间：to 取次日零点，避免用 23:59:59 漏掉最后一秒内的记录。 */
    private static LocalDateTime fromOf(DashboardQuery query) {
        return query.from() == null ? null : query.from().atStartOfDay();
    }

    private static LocalDateTime toOf(DashboardQuery query) {
        return query.to() == null ? null : query.to().plusDays(1).atStartOfDay();
    }

    /** 合格率百分比保留一位小数；无记录时为 0.0 而不是除零。 */
    private static double rate(long passed, long total) {
        return total == 0 ? 0.0 : Math.round(passed * 1000.0 / total) / 10.0;
    }

    private static long passedOf(Long passed) {
        return passed == null ? 0 : passed;
    }
}
