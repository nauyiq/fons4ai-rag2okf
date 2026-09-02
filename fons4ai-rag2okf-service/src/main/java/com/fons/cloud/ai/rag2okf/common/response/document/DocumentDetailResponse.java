package com.fons.cloud.ai.rag2okf.common.response.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.CleanupStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;

import java.util.Date;
import java.util.List;

/**
 * 文档详情安全视图。
 *
 * <p>状态与结果阶段分别表达用户语义和内部生命周期；制品对象键、数据库主键、
 * 任务快照及错误堆栈不进入响应。</p>
 *
 * @param documentKey     文档业务标识
 * @param knowledgeBaseKey 所属知识库业务标识
 * @param displayName     文档展示名称
 * @param currentFile     当前源文件安全元数据
 * @param status          用户可见状态
 * @param stage           当前结果内部阶段
 * @param parse           解析结果摘要
 * @param chunk           分块结果摘要
 * @param publication     发布结果摘要
 * @param cleanup         内容清理状态；当前活跃文档通常为 NOT_REQUIRED
 * @param latestTasks     每种任务类型最近一条安全摘要
 * @param availableActions 服务端按当前状态计算的可用动作
 * @param updatedAt       最近更新时间
 */
public record DocumentDetailResponse(
        String documentKey,
        String knowledgeBaseKey,
        String displayName,
        DocumentSummaryResponse.CurrentFileSummary currentFile,
        DocumentStatus status,
        ResultStage stage,
        ParseSummary parse,
        ChunkSummary chunk,
        PublicationSummary publication,
        CleanupStatus cleanup,
        List<DocumentTaskSummaryResponse> latestTasks,
        List<String> availableActions,
        Date updatedAt) {

    /** 解析器、结构块和警告数量摘要。 */
    public record ParseSummary(ParserType parserType, Integer blockCount, Integer warningCount) {
    }

    /** 分块策略与数量摘要。 */
    public record ChunkSummary(
            String boundaryType, String hierarchyType,
            Integer parentCount, Integer childCount, Integer totalCount) {
    }

    /** 发布投影摘要，不暴露 Elasticsearch 内部引用。 */
    public record PublicationSummary(Integer projectionCount, Date publishedAt) {
    }
}
