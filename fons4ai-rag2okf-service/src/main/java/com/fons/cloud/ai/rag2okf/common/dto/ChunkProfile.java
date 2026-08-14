package com.fons.cloud.ai.rag2okf.common.dto;

import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseException;
import com.fons.cloud.ai.rag2okf.common.request.ChunkProfileRequest;

/**
 * 知识库分块配置值对象。
 *
 * <p>集中校验分块策略名称、块大小、重叠量和标题层级，确保任务创建时复制的快照始终合法。
 * 该值对象不可变；修改知识库设置时构造新实例替换旧值，不暴露 setter。</p>
 *
 * @author hongqy
 */
public record ChunkProfile(
        String strategy,
        int chunkSize,
        int overlap,
        Integer titleLevel
) {

    private static final int MAX_STRATEGY_LENGTH = 64;
    private static final int MAX_CHUNK_SIZE = 100_000;
    private static final int MAX_TITLE_LEVEL = 10;

    /**
     * 紧凑构造器，在校验通过后冻结值对象。
     *
     * @throws KnowledgeBaseException 当策略为空、块大小非正、重叠越界或标题层级非法时
     */
    public ChunkProfile {
        if (strategy == null || strategy.isBlank() || strategy.trim().length() > MAX_STRATEGY_LENGTH) {
            throw new KnowledgeBaseException();
        }
        strategy = strategy.trim();
        if (chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE) {
            throw new KnowledgeBaseException();
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new KnowledgeBaseException();
        }
        if (titleLevel != null && (titleLevel < 1 || titleLevel > MAX_TITLE_LEVEL)) {
            throw new KnowledgeBaseException();
        }
    }

    public ChunkProfile(ChunkProfileRequest chunkProfileRequest) {
        this(
                chunkProfileRequest.strategy(),
                chunkProfileRequest.chunkSize(),
                chunkProfileRequest.overlap(),
                chunkProfileRequest.titleLevel()
        );
    }
}
