package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;

import java.util.List;

/** 将有序候选块组织成最终 ChunkManifest 的层级策略。 */
public interface ChunkHierarchyStrategy {

    /** @return 本实现唯一支持的层级策略类型。 */
    ChunkHierarchyType getType();

    /**
     * 组织候选块，不执行对象存储或数据库操作。
     *
     * @param document 已校验的解析制品
     * @param resultKey 当前解析结果业务标识
     * @param policy 冻结分块策略
     * @param candidates 有序候选块
     * @return 含完整角色和统计信息的清单
     */
    ChunkManifest organize(ParsedDocument document, String resultKey, ChunkPolicy policy,
                           List<ChunkCandidate> candidates);
}
