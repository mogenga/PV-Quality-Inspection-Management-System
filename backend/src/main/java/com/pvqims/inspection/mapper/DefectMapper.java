package com.pvqims.inspection.mapper;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pvqims.inspection.dto.DefectDetail;
import com.pvqims.inspection.entity.Defect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DefectMapper extends BaseMapper<Defect> {

    /**
     * 缺陷列表（FR-11）：读视图 v_defect_detail，联查记录过滤作废——记录作废后其派生缺陷不再出现（BR-08），
     * 但缺陷行仍在库中。可选条件为空即不过滤；to 由服务层转为次日零点，与记录查询同一闭区间口径。
     */
    @Select("""
        <script>
        SELECT d.defect_id, d.record_id, d.project_id, d.project_code, d.project_name, d.batch_no,
               d.scene, d.process_name, d.item_name, d.serial_no, d.inspect_time,
               d.std_lower, d.std_upper, d.std_target, d.std_tolerance, d.std_unit,
               d.defect_type, d.defect_grade, d.defect_status, d.handle_note, d.resolved_at,
               d.defect_created_at, d.inspector_name, d.handler_name
        FROM v_defect_detail d
        JOIN inspection_record r ON r.id = d.record_id
        WHERE r.is_void = 0
          <if test="grade != null and grade != ''">AND d.defect_grade = #{grade}</if>
          <if test="status != null and status != ''">AND d.defect_status = #{status}</if>
          <if test="processName != null and processName != ''">AND d.process_name = #{processName}</if>
          <if test="from != null">AND d.inspect_time &gt;= #{from}</if>
          <if test="to != null">AND d.inspect_time &lt; #{to}</if>
        ORDER BY d.defect_created_at DESC, d.defect_id DESC
        </script>
        """)
    IPage<DefectDetail> selectDetailPage(IPage<DefectDetail> page,
                                         @Param("grade") String grade, @Param("status") String status,
                                         @Param("processName") String processName,
                                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
