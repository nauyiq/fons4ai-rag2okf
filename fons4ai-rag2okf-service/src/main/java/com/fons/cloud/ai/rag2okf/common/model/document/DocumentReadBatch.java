package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.response.document.DocumentTaskSummaryResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import java.util.Map;
import java.util.List;

/**
 * 文档列表页批量读取的当前结果和最近任务视图。
 *
 * <p>该模型封装批量读取产物，避免应用服务持有并传递两个松散的索引 Map。</p>
 */
public class DocumentReadBatch {
    /** 按文档主键索引的当前结果。 */
    private final Map<Long, KbDocumentResult> resultsByDocumentId;

    /** 按文档主键索引的每种类型最近任务摘要。 */
    private final Map<Long, List<DocumentTaskSummaryResponse>> tasksByDocumentId;

    /**
     * 创建批量读取结果。
     *
     * @param resultsByDocumentId 文档当前结果索引
     * @param tasksByDocumentId   文档最近任务摘要索引
     */
    public DocumentReadBatch(Map<Long, KbDocumentResult> resultsByDocumentId, Map<Long, List<DocumentTaskSummaryResponse>> tasksByDocumentId) {
        this.resultsByDocumentId = Map.copyOf(resultsByDocumentId);
        this.tasksByDocumentId = Map.copyOf(tasksByDocumentId);
    }

    /**
     * 创建没有文档数据的空批量读取结果。
     *
     * @return 空读取结果
     */
    public static DocumentReadBatch empty() {
        return new DocumentReadBatch(Map.of(), Map.of());
    }

    /**
     * 读取指定文档的当前结果。
     *
     * @param documentId 文档主键
     * @return 当前结果；未找到时返回 {@code null}
     */
    public KbDocumentResult getCurrentResult(Long documentId) {
        return resultsByDocumentId.get(documentId);
    }

    /**
     * 读取指定文档的最近任务摘要。
     *
     * @param documentId 文档主键
     * @return 最近任务摘要；未找到时返回空列表
     */
    public List<DocumentTaskSummaryResponse> getTaskSummaries(Long documentId) {
        return tasksByDocumentId.getOrDefault(documentId, List.of());
    }
}
