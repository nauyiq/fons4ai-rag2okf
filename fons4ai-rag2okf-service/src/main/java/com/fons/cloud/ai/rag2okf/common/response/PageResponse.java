package com.fons.cloud.ai.rag2okf.common.response;

import java.util.List;

/**
 * 通用分页响应。
 *
 * @param records 当前页记录
 * @param total 总记录数
 * @param page 当前页码（0 基）
 * @param size 每页大小
 * @param <T> 记录类型
 * @author hongqy
 */
public record PageResponse<T>(
        List<T> records,
        long total,
        int page,
        int size
) {
}
