package com.fons.cloud.ai.rag2okf.common.dto;

/**
 * 重新分块任务输入快照，序列化为 JSON 存入 {@code kb_processing_task.payload_json}。
 *
 * @param workspaceKey             工作空间业务 key
 * @param knowledgeBaseKey         知识库业务 key
 * @param documentKey              文档业务 key
 * @param sourceDocumentId         源文档数据库主键
 * @param parseRevisionId          解析 Revision 数据库主键
 * @param parseRevisionKey         解析 Revision 业务 key
 * @param expectedChunkRevisionKey 调用方持有的当前 ChunkRevision key
 * @param chunkProfile             新分块策略
 * @author hongqy
 */
public record RechunkTaskPayload(
        String workspaceKey,
        String knowledgeBaseKey,
        String documentKey,
        Long sourceDocumentId,
        Long parseRevisionId,
        String parseRevisionKey,
        String expectedChunkRevisionKey,
        ParsingChunkProfile chunkProfile
) {
}
