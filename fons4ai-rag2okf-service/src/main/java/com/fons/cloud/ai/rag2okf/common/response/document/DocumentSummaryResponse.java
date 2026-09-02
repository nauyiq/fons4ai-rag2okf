package com.fons.cloud.ai.rag2okf.common.response.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;

import java.util.Date;
import java.util.List;

/**
 * 文档列表安全摘要，不包含数据库主键、对象存储键或文件夹语义。
 *
 * @param documentKey  文档业务标识
 * @param displayName  文档展示名称
 * @param currentFile  当前源文件安全元数据
 * @param status       用户可见状态
 * @param stage        当前结果内部阶段
 * @param latestTasks  每种任务类型最近一条安全摘要
 * @param updatedAt    最近更新时间
 */
public record DocumentSummaryResponse(
        String documentKey,
        String displayName,
        CurrentFileSummary currentFile,
        DocumentStatus status,
        ResultStage stage,
        List<DocumentTaskSummaryResponse> latestTasks,
        Date updatedAt) {

    /**
     * 当前源文件的安全元数据。
     *
     * @param fileToken   当前文件令牌
     * @param filename    原始文件名
     * @param contentType 服务端确认的 MIME 类型
     * @param sizeBytes   文件字节数
     * @param sha256      内容摘要，用于完整性展示或校验
     */
    public record CurrentFileSummary(
            String fileToken,
            String filename,
            String contentType,
            Long sizeBytes,
            String sha256) {
    }
}
