package com.pvqims.inspection;

import java.time.LocalDate;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pvqims.common.api.PageResult;
import com.pvqims.inspection.dto.DefectDetail;
import com.pvqims.inspection.mapper.DefectMapper;
import org.springframework.stereotype.Service;

/**
 * 缺陷查询（FR-11）：只读列表，按等级、状态、工序、时间段筛选。
 * 记录作废后其缺陷不在列表中（BR-08），缺陷行保留。
 */
@Service
public class DefectService {

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
}
