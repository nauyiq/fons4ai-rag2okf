package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking;

import com.fons.cloud.ai.rag2okf.common.model.document.ModelProfileReference;

import java.util.LinkedHashMap;
import java.util.Map;

/** 分块执行期所需的安全上下文，只保存模型档案引用而不保存凭据。 */
public class ChunkingExecutionContext {

    /** 发起任务的用户主键，用于用户域模型档案授权。 */
    private final Long requestedBy;

    /** 按能力代码索引的冻结模型档案引用。 */
    private final Map<String, ModelProfileReference> modelProfileRefs;

    /**
     * 创建分块执行上下文。
     *
     * @param requestedBy 发起任务的用户主键
     * @param modelProfileRefs 冻结的模型档案引用
     */
    public ChunkingExecutionContext(Long requestedBy, Map<String, ModelProfileReference> modelProfileRefs) {
        this.requestedBy = requestedBy;
        this.modelProfileRefs = modelProfileRefs == null
                ? Map.of() : Map.copyOf(new LinkedHashMap<>(modelProfileRefs));
    }

    /** @return 发起任务的用户主键。 */
    public Long getRequestedBy() {
        return requestedBy;
    }

    /** @return 冻结的模型档案引用。 */
    public Map<String, ModelProfileReference> getModelProfileRefs() {
        return modelProfileRefs;
    }
}
