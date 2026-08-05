package com.fons.cloud.ai.rag2okf.common.response;

import com.fons.cloud.ai.rag2okf.common.constants.ModelUsageType;

/**
 * 知识库模型用途绑定响应。
 *
 * <p>不返回 API Key、Base URL 或模型凭证，只返回用途与档案标识。</p>
 *
 * @param bindingKey 绑定业务标识
 * @param usageType 模型用途
 * @param modelProfileKey 模型档案业务标识
 * @author hongqy
 */
public record ModelBindingResponse(
        String bindingKey,
        ModelUsageType usageType,
        String modelProfileKey
) {
}
