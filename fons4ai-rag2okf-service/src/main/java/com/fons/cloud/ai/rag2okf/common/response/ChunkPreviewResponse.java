package com.fons.cloud.ai.rag2okf.common.response;

import com.fons.cloud.ai.rag2okf.domain.parsing.ChunkProfile;

import java.util.List;

/**
 * 分块预览响应，返回当前解析侧分块分页（AC-012、AC-013）。
 *
 * <p>无当前 ChunkRevision 时返回空 chunks 和 hasChunk=false（AC-013 不伪造结果）。
 *
 * @param hasChunk      是否存在已成功的分块产物
 * @param chunkProfile  分块策略
 * @param parentCount   父分块数量
 * @param childCount    子分块数量
 * @param contentHash   分块产物内容摘要
 * @param page          当前页码（从 0 开始）
 * @param size          每页大小
 * @param total         分块总数
 * @param chunks        当前页分块列表
 * @author hongqy
 */
public record ChunkPreviewResponse(
        boolean hasChunk,
        String currentChunkRevisionKey,
        ChunkProfile chunkProfile,
        int parentCount,
        int childCount,
        String contentHash,
        int page,
        int size,
        int total,
        List<ChunkView> chunks
) {

    /**
     * 分块视图。
     *
     * @param index          分块序号
     * @param content        分块文本内容
     * @param parentChunkId  父分块标识
     * @param skipEmbedding  是否跳过向量化
     */
    public record ChunkView(
            int index,
            String content,
            String parentChunkId,
            boolean skipEmbedding
    ) {
    }
}
