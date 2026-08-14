package com.fons.cloud.ai.rag2okf.common.dto;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;

import java.util.List;

/**
 * 发布投影端口：将 ChunkRevision 物理投影到检索索引（AC-016、AC-017、AC-018、AC-021、AC-022、AC-024）。
 *
 * <p>遵循 DDD-lite 端口适配：领域层只定义不可变值对象与契约；
 * 具体的 Elasticsearch 客户端依赖必须位于基础设施适配器。
 *
 * <p>调用方必须保证：
 * <ul>
 *   <li>调用 {@link #projectChunks} 前已写入 MinIO 的 ChunkManifest；</li>
 *   <li>写入数量与 contentHash 必须与 ChunkManifest 一致（D-002、D-007）；</li>
 *   <li>清理旧投影前已通过 MySQL CAS 完成 activePublicationRevisionId 切换。</li>
 * </ul>
 *
 * <p>本端口不承担业务授权、CAS 指针切换或 Outbox 触发；这些职责归属应用服务。
 *
 * @author hongqy
 */
public interface PublicationProjectionPort {

    /**
     * 启动物理索引与读写别名。幂等：若已存在则跳过。
     *
     * <p>本方法只在启动或运维触发时调用，发布任务执行不依赖调用此方法。
     *
     * @throws ProjectionException 启动失败
     */
    void bootstrapIndex();

    /**
     * 批量写入指定发布 Revision 的所有分块投影。
     *
     * <p>所有写入操作携带 publicationRevisionKey 与 schemaVersion；
     * 任一文档写入失败整体标记失败（§5.6 第 4 步）。
     *
     * @param request 投影请求
     * @return 投影结果，包含实际写入数量与 contentHash
     * @throws ProjectionException 写入失败
     */
    ProjectionResult projectChunks(ProjectionRequest request);

    /**
     * 删除指定发布 Revision 的所有投影（清理旧 active）。
     *
     * <p>用于 Outbox 异步清理；清理延迟不影响 active 切换（§5.6 第 7 步）。
     *
     * @param publicationRevisionKey 发布 Revision 业务标识
     * @return 实际删除数量
     * @throws ProjectionException 删除失败
     */
    long deleteByPublicationRevisionKey(String publicationRevisionKey);

    /**
     * 统计指定发布 Revision 的投影数量（用于校验写入完整性）。
     *
     * @param publicationRevisionKey 发布 Revision 业务标识
     * @return 当前投影数量
     * @throws ProjectionException 查询失败
     */
    long countByPublicationRevisionKey(String publicationRevisionKey);

    /**
     * 投影写入请求。
     *
     * @param publicationRevisionKey 发布 Revision 业务标识
     * @param workspaceKey          工作空间业务 key
     * @param knowledgeBaseKey      知识库业务 key
     * @param documentKey           文档业务 key
     * @param parseRevisionKey      解析 Revision 业务 key
     * @param chunkRevisionKey      分块 Revision 业务 key
     * @param contentHash           分块集合 SHA-256 摘要
     * @param chunks                分块投影列表
     */
    record ProjectionRequest(
            String publicationRevisionKey,
            String workspaceKey,
            String knowledgeBaseKey,
            String documentKey,
            String parseRevisionKey,
            String chunkRevisionKey,
            String contentHash,
            List<ChunkProjection> chunks
    ) {
    }

    /**
     * 单个分块的投影数据。
     *
     * @param chunkKey          分块唯一标识
     * @param parentChunkKey    父块 key；父块自身为 null
     * @param chunkLevel        分块层级：PARENT 或 CHILD
     * @param ordinal           文档内顺序，从 0 开始
     * @param rawText           原始文本，仅供调试与追溯
     * @param displayText       展示文本，参与 BM25
     * @param embeddingText     向量化输入文本（CR-013：发布时同步生成 vector）
     * @param titlePath         结构化标题路径（如 "第1章/1.1 概述"），可空
     * @param sourceLocatorType 来源定位类型：PAGE / BLOCK / NONE
     * @param pageNumber        PDF 页码，可空（仅 PAGE 时有值）
     * @param startOffset       文本起始偏移，可空
     * @param endOffset         文本结束偏移，可空
     * @param vector            发布时同步计算的向量（CR-013，D-007）；
     *                          skipEmbedding chunk 或无 EMBEDDING 绑定时为 null
     */
    record ChunkProjection(
            String chunkKey,
            String parentChunkKey,
            String chunkLevel,
            int ordinal,
            String rawText,
            String displayText,
            String embeddingText,
            String titlePath,
            String sourceLocatorType,
            Integer pageNumber,
            Integer startOffset,
            Integer endOffset,
            float[] vector
    ) {
        /** 父块层级。 */
        public static final String LEVEL_PARENT = "PARENT";
        /** 子块层级。 */
        public static final String LEVEL_CHILD = "CHILD";
    }

    /**
     * 投影写入结果。
     *
     * @param projectionIndex  实际写入的物理索引名称
     * @param projectionCount  实际写入的分块数量
     * @param contentHash      分块集合摘要（来自请求）
     */
    record ProjectionResult(
            String projectionIndex,
            int projectionCount,
            String contentHash
    ) {
    }

    /**
     * 投影异常，携带安全化错误码。
     */
    class ProjectionException extends RuntimeException {

        private final Rag2OkfResultCode errorCode;

        /**
         * 创建不携带底层原因的投影异常。
         *
         * @param errorCode 项目统一错误码
         * @param message 内部诊断消息
         */
        public ProjectionException(Rag2OkfResultCode errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        /**
         * 创建保留底层原因的投影异常。
         *
         * @param errorCode 项目统一错误码
         * @param message 内部诊断消息
         * @param cause 底层异常
         */
        public ProjectionException(Rag2OkfResultCode errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        /**
         * 获取写入任务记录的 RF 错误码。
         *
         * @return RF 错误码
         */
        public String errorCode() {
            return errorCode.getCode();
        }
    }
}
