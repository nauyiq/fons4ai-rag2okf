package com.fons.cloud.ai.rag2okf.common.model.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;

/**
 * 已通过所有权、状态和端点校验的非敏感模型调用描述。
 *
 * @param profileKey 模型档案业务标识
 * @param modelType 调用能力类型
 * @param baseUrl 已验证的模型 API 根地址
 * @param modelName 用户选择的厂商模型 ID
 * @param dimensions 向量维度提示，仅 EMBEDDING 可用
 * @param timeoutSeconds 受控请求超时秒数
 * @param temperature 受控对话温度，仅 CHAT 可用
 * @author hongqy
 */
public record ResolvedModelDescriptor(
        String profileKey,
        ModelType modelType,
        String baseUrl,
        String modelName,
        Integer dimensions,
        Integer timeoutSeconds,
        Double temperature
) {
}
