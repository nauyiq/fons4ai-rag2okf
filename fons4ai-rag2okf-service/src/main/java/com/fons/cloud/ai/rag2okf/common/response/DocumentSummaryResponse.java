package com.fons.cloud.ai.rag2okf.common.response;

import java.util.Date;

/**
 * 文档列表项，只呈现当前文件与当前处理事实，不暴露历史版本。
 *
 * @param documentKey 文档业务标识
 * @param displayName 文档展示名称
 * @param folderPath 文件夹路径，根级为 /
 * @param currentFile 当前文件摘要
 * @param currentFileToken 当前文件 CAS 令牌，仅用于更新请求，不得展示或持久化
 * @param parseStatus 当前解析状态
 * @param publishStatus 当前发布状态
 * @param hasActivePublication 是否仍有可用的当前发布内容
 * @param latestTask 最近任务安全化摘要，可为空
 * @param updated 最近更新时间
 * @author hongqy
 */
public record DocumentSummaryResponse(
        String documentKey, String displayName, String folderPath, CurrentFileSummary currentFile, String currentFileToken,
        String parseStatus, String publishStatus, boolean hasActivePublication,
        DocumentTaskSummaryResponse latestTask, Date updated) {

    /** 当前文件摘要。 */
    public record CurrentFileSummary(String filename, String contentType, long size) {
    }
}
