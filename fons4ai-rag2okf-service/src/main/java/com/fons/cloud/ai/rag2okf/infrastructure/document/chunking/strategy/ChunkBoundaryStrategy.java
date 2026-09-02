package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.ChunkingExecutionContext;

import java.util.List;

/**
 * 按内容边界把 ParsedDocument 转换为有序候选块的技术策略。
 *
 * @author hongqy
 */
public interface ChunkBoundaryStrategy {

    /**
     * @return 本实现唯一支持的边界策略类型。
     */
    ChunkBoundaryType getType();

    /**
     * 生成候选块，不写入存储也不修改文档状态。
     *
     * @param document 已校验的解析制品
     * @param policy   冻结分块策略
     * @param context  执行上下文
     * @return 有序且内容非空的候选块
     */
    List<ChunkCandidate> split(ParsedDocument document, ChunkPolicy policy, ChunkingExecutionContext context);
}
