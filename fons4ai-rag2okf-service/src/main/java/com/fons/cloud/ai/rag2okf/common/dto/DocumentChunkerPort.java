package com.fons.cloud.ai.rag2okf.common.dto;

/**
 * 文档分块端口，由基础设施适配器实现。
 *
 * <p>适配器负责：
 * <ol>
 *   <li>从 MinIO 读取 ParseManifest</li>
 *   <li>重建 LangChain4j Document 并分块</li>
 *   <li>应用 Parent/Child 组装策略</li>
 *   <li>规范化为 ChunkManifest 并写入 MinIO</li>
 * </ol>
 *
 * @author hongqy
 */
public interface DocumentChunkerPort {

    /**
     * 对已解析文档执行分块。
     *
     * @param request 分块请求
     * @return 分块结果，包含 Manifest 和 MinIO 存储信息
     */
    ChunkResult chunk(ChunkRequest request);

    /**
     * 分块请求。
     *
     * @param scope             MinIO 对象范围
     * @param parseRevisionKey  输入解析 revision 业务标识（用于读取 ParseManifest）
     * @param chunkRevisionKey  分块 revision 业务标识（用于 MinIO 路径）
     * @param chunkProfile      分块策略配置
     */
    record ChunkRequest(
            DocumentArtifactStore.ArtifactScope scope,
            String parseRevisionKey,
            String chunkRevisionKey,
            ParsingChunkProfile chunkProfile
    ) {
    }

    /**
     * 分块结果。
     *
     * @param manifest         分块 Manifest
     * @param manifestArtifact ChunkManifest 在 MinIO 的存储信息
     */
    record ChunkResult(
            ChunkManifest manifest,
            DocumentArtifactStore.StoredArtifact manifestArtifact
    ) {
    }
}
