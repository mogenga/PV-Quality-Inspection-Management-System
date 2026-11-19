package com.pvqims.dashboard;

import java.time.LocalDate;

/**
 * 看板通用筛选条件：项目、批次、场景、工序、时间段（SRS 4.7）。六项均可为空，为空即不过滤。
 * from/to 是闭区间的日期，服务层转成次日零点做时间比较。
 */
public record DashboardQuery(Long projectId, Long batchId, LocalDate from, LocalDate to, String scene,
                             String processName) {
}
