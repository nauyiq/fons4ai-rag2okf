package com.fons.cloud.ai.rag2okf.common.request;

import com.fons.cloud.ai.rag2okf.common.constants.ModelUsageType;

/**
 * 知识库模型用途绑定项，用于创建知识库和保存模型绑定时提交单个用途与档案的对应关系。
 *
 * @param modelProfileKey 模型档案业务标识
 * @param usageType 模型用途
 * @author hongqy
 */
public record ModelBindingItem(
        ModelUsageType usageType,
        String modelProfileKey
) {
}
