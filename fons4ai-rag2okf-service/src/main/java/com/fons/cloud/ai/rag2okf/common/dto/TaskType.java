package com.fons.cloud.ai.rag2okf.common.dto;

/**
 * 异步处理任务类型。
 *
 * <p>每种类型对应一个任务执行端口实现，由 {@code DistributedLockedTaskExecutor} 分发。
 *
 * @author hongqy
 */
public enum TaskType {

    /** 文档解析：生成 ParseRevision 和 ChunkRevision。 */
    PARSE,

    /** 重新分块：基于已有 ParseRevision 生成新 ChunkRevision。 */
    RECHUNK,

    /** 发布：将 ChunkRevision 投影到 Elasticsearch。 */
    PUBLISH
}
