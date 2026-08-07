package com.fons.cloud.ai.rag2okf.common.dto;

/**
 * 不可枚举模型业务主键生成端口。
 *
 * @author hongqy
 */
public interface ModelBusinessKeyGenerator {

    /**
     * 生成 26 位 ULID 兼容业务主键。
     *
     * @return 用于 connectionKey 或 profileKey 的业务主键
     */
    String nextKey();
}
