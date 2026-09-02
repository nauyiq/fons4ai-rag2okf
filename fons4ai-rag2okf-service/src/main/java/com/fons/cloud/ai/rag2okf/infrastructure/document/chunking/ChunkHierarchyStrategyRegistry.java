package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkHierarchyStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 按层级枚举选择唯一策略实现的注册表，不提供默认回退。 */
@Component
public class ChunkHierarchyStrategyRegistry {

    /** 已注册的策略实现。 */
    private final Map<ChunkHierarchyType, ChunkHierarchyStrategy> strategies;

    /**
     * 注入所有 Spring 策略实现并拒绝重复注册。
     *
     * @param implementations 层级策略实现列表
     */
    public ChunkHierarchyStrategyRegistry(List<ChunkHierarchyStrategy> implementations) {
        this.strategies = new EnumMap<>(ChunkHierarchyType.class);
        for (ChunkHierarchyStrategy implementation : implementations) {
            if (strategies.put(implementation.getType(), implementation) != null) {
                throw new IllegalStateException("重复的分块层级策略注册: " + implementation.getType());
            }
        }
    }

    /**
     * 严格获取指定策略。
     *
     * @param type 层级策略类型
     * @return 已注册实现
     * @throws DocumentProcessingException 未知或缺失实现时抛出分块策略非法错误
     */
    public ChunkHierarchyStrategy require(ChunkHierarchyType type) {
        ChunkHierarchyStrategy strategy = type == null ? null : strategies.get(type);
        if (strategy == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        return strategy;
    }
}
