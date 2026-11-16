package com.pvqims.common.api;

import java.util.List;

/**
 * 分页查询结果。
 */
public record PageResult<T>(List<T> records, long total, long page, long pageSize) {
}
