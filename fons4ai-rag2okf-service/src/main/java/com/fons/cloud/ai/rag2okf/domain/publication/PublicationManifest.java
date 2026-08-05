package com.fons.cloud.ai.rag2okf.domain.publication;

import java.util.List;

/**
 * 发布快照 Manifest，描述一次发布的完整可重建投影元数据（技术设计 §4.8、§5.6）。
 *
 * <p>不可变值对象，写入 MinIO 后以 MySQL {@code active_publication_revision_id} 指针切换生效。
 * 投影索引只在 ES 中存在，但发布 manifest 是可重建的来源（D-002：MySQL 为事实，ES 是投影）。
 *
 * @param publicationRevisionKey 发布 Revision 业务标识
 * @param workspaceKey          工作空间业务 key
 * @param knowledgeBaseKey      知识库业务 key
 * @param documentKey           文档业务 key
 * @param documentVersionKey    文档版本业务 key
 * @param parseRevisionKey      解析 Revision 业务 key
 * @param chunkRevisionKey      分块 Revision 业务 key
 * @param chunkCount            投影分块数量（含父块）
 * @param contentHash           分块集合 SHA-256 摘要
 * @param schemaVersion         投影 schema 版本（v1）
 * @param triggerType           触发方式：MANUAL 或 AUTO
 * @param publishedAt           发布时间（成功发布时填入）
 * @author hongqy
 */
public record PublicationManifest(
        String publicationRevisionKey,
        String workspaceKey,
        String knowledgeBaseKey,
        String documentKey,
        String documentVersionKey,
        String parseRevisionKey,
        String chunkRevisionKey,
        int chunkCount,
        String contentHash,
        String schemaVersion,
        String triggerType,
        String publishedAt
) {

    /** 手动触发。 */
    public static final String TRIGGER_MANUAL = "MANUAL";
    /** 自动触发。 */
    public static final String TRIGGER_AUTO = "AUTO";

    /** Schema V1。 */
    public static final String SCHEMA_V1 = "v1";
}
