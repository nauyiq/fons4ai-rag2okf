package com.fons.cloud.ai.rag2okf.domain.parsing;

import java.util.List;
import java.util.Map;

/**
 * 分块产物 Manifest，描述一次分块的完整结果。
 *
 * <p>不可变值对象，写入 MinIO 后以数据库指针切换生效。
 * childCount=0 表示分块失败，调用方应拒绝发布。
 *
 * @param chunkRevisionKey 分块 revision 业务标识
 * @param parseRevisionKey 输入解析 revision 业务标识
 * @param chunkProfile     分块策略配置
 * @param parentCount      父块数量
 * @param childCount       子块数量（含父块中 skipEmbedding=true 的块）
 * @param chunks           分块列表
 * @param contentHash      分块内容 SHA-256 摘要
 * @author hongqy
 */
public record ChunkManifest(
        String chunkRevisionKey,
        String parseRevisionKey,
        ChunkProfile chunkProfile,
        int parentCount,
        int childCount,
        List<Chunk> chunks,
        String contentHash
) {
    /**
     * 分块。
     *
     * @param index         块序号，从 0 开始
     * @param content       块文本内容
     * @param parentChunkId 父块 ID；父块自身为 null
     * @param skipEmbedding 是否跳过向量化（父块标记为 true，仅存储供检索时召回）
     * @param metadata      块元数据（标题层级等）
     */
    public record Chunk(
            int index,
            String content,
            String parentChunkId,
            boolean skipEmbedding,
            Map<String, Object> metadata
    ) {
    }
}
