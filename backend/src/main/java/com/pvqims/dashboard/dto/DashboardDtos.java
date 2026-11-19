package com.pvqims.dashboard.dto;

import java.time.LocalDate;

/**
 * 看板对外响应类型。全部为 record——MyBatis 映射用的行对象是
 * {@code DashboardMapper} 里的 {@code TrendRow} / {@code ProcessRow}，
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
}
