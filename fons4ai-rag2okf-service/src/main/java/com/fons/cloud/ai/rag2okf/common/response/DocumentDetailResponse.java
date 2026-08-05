package com.fons.cloud.ai.rag2okf.common.response;

/**
 * 文档详情响应。
 *
 * <p>不返回版本序号、版本列表或回退操作（D-004）。</p>
 *
 * @param documentKey 文档业务标识
 * @param knowledgeBaseKey 所属知识库业务标识
 * @param displayName 文档展示名称
 * @param currentFile 当前文件摘要
 * @param currentFileToken 当前文件 CAS 令牌，仅用于更新请求，不得展示或持久化
 * @param parseStatus 当前解析状态
 * @param publishStatus 当前发布状态
 * @param hasActivePublication 是否存在当前可用发布内容
 * @param latestTask 最近任务安全化摘要，可为空
 * @param updated 最近更新时间
 * @author hongqy
 */
public record DocumentDetailResponse(
        String documentKey,
        String knowledgeBaseKey,
        String displayName,
        CurrentFileSummary currentFile,
        String currentFileToken,
        String parseStatus,
        String publishStatus,
        boolean hasActivePublication,
        DocumentTaskSummaryResponse latestTask,
        java.util.Date updated
) {

    /**
     * 当前文件摘要。
     *
     * @param filename 文件名
     * @param contentType MIME 类型
     * @param size 文件字节数
     */
    public record CurrentFileSummary(String filename, String contentType, long size) {
    }
}
