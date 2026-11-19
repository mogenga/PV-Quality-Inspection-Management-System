package com.pvqims.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 看板聚合查询（FR-13、FR-14）。全部实时查询，不做预聚合（SRS 4.7）。
 * 每条 SQL 的 WHERE 都带 is_void = 0——作废记录不参与任何统计（BR-08、AC-06）。
 * 行对象是本接口内的静态嵌套类：MyBatis 按 setter 映射，不能换成 record。
 */
@Mapper
public interface DashboardMapper {

    /**
     * CH-01 每日合格率趋势：按 inspect_time 的日期聚合。to 由服务层转为次日零点，与记录查询同一闭区间口径。
     * SUM(result = 'PASS') 无行时为 NULL，故用装箱类型承接，服务层按 0 处理。
     */
    @Select("""
        <script>
        SELECT DATE(inspect_time) AS day, COUNT(*) AS total, SUM(result = 'PASS') AS passed
        FROM inspection_record
        WHERE is_void = 0
          <if test="projectId != null">AND project_id = #{projectId}</if>
          <if test="batchId != null">AND batch_id = #{batchId}</if>
          <if test="from != null">AND inspect_time &gt;= #{from}</if>
          <if test="to != null">AND inspect_time &lt; #{to}</if>
        GROUP BY DATE(inspect_time)
        ORDER BY day
        </script>
        """)
    List<TrendRow> selectTrend(@Param("projectId") Long projectId, @Param("batchId") Long batchId,
                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * CH-03 工序合格率对比：按 process_name 聚合，按合格率升序——最需要关注的工序排在最前。
     */
    @Select("""
        <script>
        SELECT process_name AS processName, COUNT(*) AS total, SUM(result = 'PASS') AS passed
        FROM inspection_record
        WHERE is_void = 0
          <if test="projectId != null">AND project_id = #{projectId}</if>
          <if test="batchId != null">AND batch_id = #{batchId}</if>
          <if test="from != null">AND inspect_time &gt;= #{from}</if>
          <if test="to != null">AND inspect_time &lt; #{to}</if>
        GROUP BY process_name
        ORDER BY passed / total
        </script>
        """)
    List<ProcessRow> selectProcessComparison(@Param("projectId") Long projectId,
                                             @Param("batchId") Long batchId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /**
     * 指标卡的「未关闭缺陷数」：CLOSED 之外都算未关闭；来源记录已作废的缺陷不计数。
     */
    @Select("""
        <script>
        SELECT COUNT(*)
        FROM defect d
        JOIN inspection_record r ON r.id = d.record_id
        WHERE r.is_void = 0
          AND d.defect_status &lt;&gt; 'CLOSED'
          <if test="projectId != null">AND r.project_id = #{projectId}</if>
          <if test="batchId != null">AND r.batch_id = #{batchId}</if>
          <if test="from != null">AND r.inspect_time &gt;= #{from}</if>
          <if test="to != null">AND r.inspect_time &lt; #{to}</if>
        </script>
        """)
    long countOpenDefects(@Param("projectId") Long projectId, @Param("batchId") Long batchId,
                          @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 指标卡的「平均处理时长」（小时）：只统计已关闭且写有关闭时间的缺陷；无数据时 AVG 为 NULL，
     * 服务层原样返回 null（0 小时与没有数据不是一回事）。保留一位小数与 SRS 5.10 一致。
     */
    @Select("""
        <script>
        SELECT ROUND(AVG(TIMESTAMPDIFF(HOUR, d.created_at, d.resolved_at)), 1) AS avgHandleHours
        FROM defect d
        JOIN inspection_record r ON r.id = d.record_id
        WHERE r.is_void = 0
          AND d.defect_status = 'CLOSED'
          AND d.resolved_at IS NOT NULL
          <if test="projectId != null">AND r.project_id = #{projectId}</if>
          <if test="batchId != null">AND r.batch_id = #{batchId}</if>
          <if test="from != null">AND r.inspect_time &gt;= #{from}</if>
          <if test="to != null">AND r.inspect_time &lt; #{to}</if>
        </script>
        """)
    Double selectAvgHandleHours(@Param("projectId") Long projectId, @Param("batchId") Long batchId,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 趋势行。passed 用装箱类型承接 SUM 的 NULL。 */
    class TrendRow {

        private LocalDate day;
        private long total;
        private Long passed;

        public LocalDate getDay() {
            return day;
        }

        public void setDay(LocalDate day) {
            this.day = day;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public Long getPassed() {
            return passed;
        }

        public void setPassed(Long passed) {
            this.passed = passed;
        }
    }

    /** 工序对比行。 */
    class ProcessRow {

        private String processName;
        private long total;
        private Long passed;

        public String getProcessName() {
            return processName;
        }

        public void setProcessName(String processName) {
            this.processName = processName;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public Long getPassed() {
            return passed;
        }

        public void setPassed(Long passed) {
            this.passed = passed;
        }
    }
}
