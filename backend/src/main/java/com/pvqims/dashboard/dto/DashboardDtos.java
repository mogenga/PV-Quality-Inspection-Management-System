package com.pvqims.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板对外响应类型。全部为 record——MyBatis 映射用的行对象是
 * {@code DashboardMapper} 里的 {@code TrendRow} / {@code ProcessRow} 等 POJO，
 * 两者刻意分开（record 不能按 setter 映射）。
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /**
     * 指标卡：记录总数、合格数、合格率（一位小数）、未关闭缺陷数、平均处理小时数
     * （无已关闭缺陷时为 null，表示「没有数据」而非 0 小时）。
     */
    public record Summary(long totalRecords, long passRecords, double passRate, long openDefects,
                          Double avgHandleHours) {
    }

    /**
     * CH-01 每日合格率趋势的一个点。
     */
    public record TrendPoint(LocalDate day, long total, long passed, double passRate) {
    }

    /**
     * CH-03 工序合格率对比的一行。
     */
    public record ProcessComparison(String processName, long total, long passed, double passRate) {
    }

    /**
     * CH-02 缺陷帕累托的一行：出现次数与累计百分比（一位小数，末项恒为 100.0）。
     */
    public record ParetoItem(String defectType, long count, double cumulativePercent) {
    }

    /**
     * CH-05 缺陷等级构成的一行：按场景 × 等级计数。
     */
    public record GradeSceneCount(String scene, String defectGrade, long count) {
    }

    /**
     * CH-08 缺陷处理时长的一行：某等级已关闭缺陷的平均处理小时数（一位小数）。
     */
    public record DefectDuration(String defectGrade, Double avgHours) {
    }

    /**
     * CH-04 参数控制图的一个点：某次实测值与检验时间。
     */
    public record ControlPoint(LocalDateTime inspectTime, BigDecimal measuredValue) {
    }

    /**
     * CH-04 参数控制图：控制限取自标准的上下限，不另行计算（SRS 6.3、AC-09）。
     * 单侧判定方式没有的那一侧控制限为 null（GT 无上限、LT 无下限），前端据此只画存在的线。
     */
    public record ControlChart(String itemName, String unit, BigDecimal ucl, BigDecimal lcl, BigDecimal cl,
                               List<ControlPoint> points) {
    }

    /**
     * CH-07 检验员工作量的一行：某检验员的记录数。
     */
    public record InspectorWorkload(Long inspectorId, String inspectorName, long count) {
    }

    /**
     * CH-06 批次合格率的一行，同时服务 FR-13 的批次汇总表。
     */
    public record BatchPassRate(Long batchId, String batchNo, long total, long passed, double passRate) {
    }
}
