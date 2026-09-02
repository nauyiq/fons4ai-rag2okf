package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkBoundaryStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 按边界枚举选择唯一策略实现的注册表，不提供默认回退。 */
@Component
public class ChunkBoundaryStrategyRegistry {

    /** 已注册的策略实现。 */
    private final Map<ChunkBoundaryType, ChunkBoundaryStrategy> strategies;

    /**
     * 注入所有 Spring 策略实现并拒绝重复注册。
     *
     * @param implementations 边界策略实现列表
     */
    public ChunkBoundaryStrategyRegistry(List<ChunkBoundaryStrategy> implementations) {
        this.strategies = new EnumMap<>(ChunkBoundaryType.class);
        for (ChunkBoundaryStrategy implementation : implementations) {
            if (strategies.put(implementation.getType(), implementation) != null) {
                throw new IllegalStateException("重复的分块边界策略注册: " + implementation.getType());
            }
        }
    }

    /**
     * 严格获取指定策略。
     *
     * @param type 边界策略类型
     * @return 已注册实现
     * @throws DocumentProcessingException 未知或缺失实现时抛出分块策略非法错误
     */
    public ChunkBoundaryStrategy require(ChunkBoundaryType type) {
        ChunkBoundaryStrategy strategy = type == null ? null : strategies.get(type);
        if (strategy == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        return strategy;
    }
}
