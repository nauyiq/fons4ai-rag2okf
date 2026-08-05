package com.fons.cloud.ai.rag2okf.application.publication;

/**
 * 发布任务输入快照，序列化为 JSON 存入 {@code kb_processing_task.payload_json}。
 *
 * <p>任务执行时只读取此快照，不读取可能已变化的"当前"内容（技术设计 §5.7）。
 * 输入引用不可变 Version/Parse/Chunk key，保证重试结果一致。
 *
 * @param workspaceKey            工作空间业务 key
 * @param knowledgeBaseKey        知识库业务 key
 * @param documentKey             文档业务 key
 * @param sourceDocumentId        源文档数据库主键
 * @param documentVersionId       文件版本数据库主键
 * @param versionKey              文件版本业务 key
 * @param parseRevisionId         解析 Revision 数据库主键
 * @param parseRevisionKey        解析 Revision 业务 key
 * @param chunkRevisionId         分块 Revision 数据库主键
 * @param chunkRevisionKey        分块 Revision 业务 key
 * @param triggerType             触发方式：MANUAL 或 AUTO
 * @author hongqy
 */
public record PublicationTaskPayload(
        String workspaceKey,
        String knowledgeBaseKey,
        String documentKey,
        Long sourceDocumentId,
        Long documentVersionId,
        String versionKey,
        Long parseRevisionId,
        String parseRevisionKey,
        Long chunkRevisionId,
        String chunkRevisionKey,
        String triggerType
) {
}
