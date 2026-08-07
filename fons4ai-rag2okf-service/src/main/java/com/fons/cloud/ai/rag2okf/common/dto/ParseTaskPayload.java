package com.fons.cloud.ai.rag2okf.common.dto;

/**
 * 解析任务输入快照，序列化为 JSON 存入 {@code kb_processing_task.payload_json}。
 *
 * <p>任务执行时只读取此快照，不读取可能已变化的"当前"内容（技术设计 §5.7）。
 *
 * @param workspaceKey      工作空间业务 key
 * @param knowledgeBaseKey  知识库业务 key
 * @param documentKey       文档业务 key
 * @param sourceDocumentId  源文档数据库主键
 * @param fileToken         文件更新 CAS 令牌，标识当前文件内容
 * @param originalFilename  已净化的原始文件名
 * @param contentType       已验证的 MIME 类型
 * @param parserProfile     解析器 Profile：NATIVE_TIKA 或 MINERU_LAYOUT
 * @param chunkProfile      分块策略
 * @param autoPublish       解析成功后是否自动发布
 * @author hongqy
 */
public record ParseTaskPayload(
        String workspaceKey,
        String knowledgeBaseKey,
        String documentKey,
        Long sourceDocumentId,
        String fileToken,
        String originalFilename,
        String contentType,
        String parserProfile,
        ParsingChunkProfile chunkProfile,
        boolean autoPublish
) {
}
