package com.pvqims.dashboard;

import java.math.BigDecimal;
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
     * 第二排序键 process_name 让合格率相同时的顺序稳定，同一请求的结果不会在两次查询间重排。
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
        ORDER BY passed / total, process_name
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

    /**
     * CH-02 缺陷帕累托：按 defect_type 计数降序（SRS 5.10）。第二排序键 defect_type 让并列时结果稳定。
     * 缺陷表没有 project_id，项目/批次/时间筛选都联 inspection_record 做（含 is_void）。
     */
    @Select("""
        <script>
        SELECT d.defect_type AS defectType, COUNT(*) AS cnt
        FROM defect d
        JOIN inspection_record r ON r.id = d.record_id
        WHERE r.is_void = 0
          <if test="projectId != null">AND r.project_id = #{projectId}</if>
          <if test="batchId != null">AND r.batch_id = #{batchId}</if>
          <if test="from != null">AND r.inspect_time &gt;= #{from}</if>
          <if test="to != null">AND r.inspect_time &lt; #{to}</if>
        GROUP BY d.defect_type
        ORDER BY cnt DESC, d.defect_type
        </script>
        """)
    List<ParetoRow> selectPareto(@Param("projectId") Long projectId, @Param("batchId") Long batchId,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * CH-05 缺陷等级构成：按场景 × 等级计数，场景用中文名由前端映射，后端只返回枚举值。
     */
    @Select("""
        <script>
        SELECT r.scene AS scene, d.defect_grade AS defectGrade, COUNT(*) AS cnt
        FROM defect d
        JOIN inspection_record r ON r.id = d.record_id
        WHERE r.is_void = 0
          <if test="projectId != null">AND r.project_id = #{projectId}</if>
          <if test="batchId != null">AND r.batch_id = #{batchId}</if>
          <if test="from != null">AND r.inspect_time &gt;= #{from}</if>
          <if test="to != null">AND r.inspect_time &lt; #{to}</if>
        GROUP BY r.scene, d.defect_grade
        ORDER BY r.scene, d.defect_grade
        </script>
        """)
    List<GradeRow> selectDefectGradeByScene(@Param("projectId") Long projectId,
                                            @Param("batchId") Long batchId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    /**
     * CH-08 缺陷处理时长：只统计已关闭且写有关闭时间的缺陷，按等级取平均小时数（SRS 5.10）。
     * 排序键让同一请求的分组柱顺序稳定。一个等级都没有已关闭缺陷时结果为空列表——「没有数据」不冒充 0 小时。
     * avgHours 用装箱类型：AVG 理论上可为 NULL，但 WHERE 里的 CLOSED 与 resolved_at IS NOT NULL
     * 让它实际不可达，服务层因此不再判空。
     */
    @Select("""
        <script>
        SELECT d.defect_grade AS defectGrade,
               AVG(TIMESTAMPDIFF(HOUR, d.created_at, d.resolved_at)) AS avgHours
        FROM defect d
        JOIN inspection_record r ON r.id = d.record_id
        WHERE r.is_void = 0
          AND d.defect_status = 'CLOSED'
          AND d.resolved_at IS NOT NULL
          <if test="projectId != null">AND r.project_id = #{projectId}</if>
          <if test="batchId != null">AND r.batch_id = #{batchId}</if>
          <if test="from != null">AND r.inspect_time &gt;= #{from}</if>
          <if test="to != null">AND r.inspect_time &lt; #{to}</if>
        GROUP BY d.defect_grade
        ORDER BY d.defect_grade
        </script>
        """)
    List<DurationRow> selectDefectDuration(@Param("projectId") Long projectId,
                                           @Param("batchId") Long batchId,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * CH-04 控制图取数（SRS 5.10 原样）：某检验项最近 30 次实测值，时间倒序取最近，服务层再反转成升序。
     * 只取有实测值的记录——文字型判定方式没有控制图。
     */
    @Select("""
        SELECT inspect_time AS inspectTime, measured_value AS measuredValue
        FROM inspection_record
        WHERE standard_id = #{standardId}
          AND measured_value IS NOT NULL
          AND is_void = 0
        ORDER BY inspect_time DESC, id DESC
        LIMIT 30
        """)
    List<ControlPointRow> selectControlPoints(@Param("standardId") Long standardId);

    /**
     * CH-07 检验员工作量：按检验员计数，次数降序；第二排序键 inspector_id 让并列时顺序稳定。
     */
    @Select("""
        <script>
        SELECT r.inspector_id AS inspectorId, u.display_name AS inspectorName, COUNT(*) AS cnt
        FROM inspection_record r
        JOIN app_user u ON u.id = r.inspector_id
        WHERE r.is_void = 0
          <if test="projectId != null">AND r.project_id = #{projectId}</if>
          <if test="batchId != null">AND r.batch_id = #{batchId}</if>
          <if test="from != null">AND r.inspect_time &gt;= #{from}</if>
          <if test="to != null">AND r.inspect_time &lt; #{to}</if>
        GROUP BY r.inspector_id, u.display_name
        ORDER BY cnt DESC, r.inspector_id
        </script>
        """)
    List<WorkloadRow> selectInspectorWorkload(@Param("projectId") Long projectId,
                                              @Param("batchId") Long batchId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /**
     * CH-06 批次合格率（同时服务 FR-13 的批次汇总表）：按批号排序，passRate 由服务层算。
     * passed 用装箱类型承接 SUM 的 NULL。
     */
    @Select("""
        <script>
        SELECT r.batch_id AS batchId, b.batch_no AS batchNo,
               COUNT(*) AS total, SUM(r.result = 'PASS') AS passed
        FROM inspection_record r
        JOIN batch b ON b.id = r.batch_id
        WHERE r.is_void = 0
          <if test="projectId != null">AND r.project_id = #{projectId}</if>
          <if test="batchId != null">AND r.batch_id = #{batchId}</if>
          <if test="from != null">AND r.inspect_time &gt;= #{from}</if>
          <if test="to != null">AND r.inspect_time &lt; #{to}</if>
        GROUP BY r.batch_id, b.batch_no
        ORDER BY b.batch_no
        </script>
        """)
    List<BatchPassRateRow> selectBatchPassRates(@Param("projectId") Long projectId,
                                                @Param("batchId") Long batchId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

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

    /** 帕累托行。 */
    class ParetoRow {

        private String defectType;
        private long cnt;

        public String getDefectType() {
            return defectType;
        }

        public void setDefectType(String defectType) {
            this.defectType = defectType;
        }

        public long getCnt() {
            return cnt;
        }

        public void setCnt(long cnt) {
            this.cnt = cnt;
        }
    }

    /** 等级构成行。 */
    class GradeRow {

        private String scene;
        private String defectGrade;
        private long cnt;

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }

        public String getDefectGrade() {
            return defectGrade;
        }

        public void setDefectGrade(String defectGrade) {
            this.defectGrade = defectGrade;
        }

        public long getCnt() {
            return cnt;
        }

        public void setCnt(long cnt) {
            this.cnt = cnt;
        }
    }

    /** 处理时长行。avgHours 用装箱类型承接 AVG 的 NULL（WHERE 已排除 NULL，实际不会出现）。 */
    class DurationRow {

        private String defectGrade;
        private Double avgHours;

        public String getDefectGrade() {
            return defectGrade;
        }

        public void setDefectGrade(String defectGrade) {
            this.defectGrade = defectGrade;
        }

        public Double getAvgHours() {
            return avgHours;
        }

        public void setAvgHours(Double avgHours) {
            this.avgHours = avgHours;
        }
    }

    /** 控制图点行。 */
    class ControlPointRow {

        private LocalDateTime inspectTime;
        private BigDecimal measuredValue;

        public LocalDateTime getInspectTime() {
            return inspectTime;
        }

        public void setInspectTime(LocalDateTime inspectTime) {
            this.inspectTime = inspectTime;
        }

        public BigDecimal getMeasuredValue() {
            return measuredValue;
        }

        public void setMeasuredValue(BigDecimal measuredValue) {
            this.measuredValue = measuredValue;
        }
    }

    /** 检验员工作量行。 */
    class WorkloadRow {

        private Long inspectorId;
        private String inspectorName;
        private long cnt;

        public Long getInspectorId() {
            return inspectorId;
        }

        public void setInspectorId(Long inspectorId) {
            this.inspectorId = inspectorId;
        }

        public String getInspectorName() {
            return inspectorName;
        }

        public void setInspectorName(String inspectorName) {
            this.inspectorName = inspectorName;
        }

        public long getCnt() {
            return cnt;
        }

        public void setCnt(long cnt) {
            this.cnt = cnt;
        }
    }

    /** 批次合格率行。passed 用装箱类型承接 SUM 的 NULL。 */
    class BatchPassRateRow {

        private Long batchId;
        private String batchNo;
        private long total;
        private Long passed;

        public Long getBatchId() {
            return batchId;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public void setBatchNo(String batchNo) {
            this.batchNo = batchNo;
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
