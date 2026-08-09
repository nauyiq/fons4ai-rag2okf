package com.fons.cloud.ai.rag2okf.common.request;

import com.fons.cloud.ai.rag2okf.common.constants.ModelType;

/**
 * 创建用户模型档案的请求。
 *
 * @param connectionKey 当前用户拥有的 Provider 连接标识
 * @param modelType 模型调用类型，7 枚举白名单之一，拒绝 CHAT
 * @param modelName 厂商实际模型 ID
 * @param dimensions 向量维度提示，仅 EMBEDDING 可设置
 * @param contextWindowLength 上下文窗口长度，由前端维护，NULL 表示未设置
 * @param timeoutSeconds 受控请求超时秒数，范围 1 到 120
 * @param temperature 受控对话温度，仅 LLM 可设置
 * @author hongqy
 */
public record CreateModelProfileRequest(
        String connectionKey,
        ModelType modelType,
        String modelName,
        Integer dimensions,
        Integer contextWindowLength,
        Integer timeoutSeconds,
        Double temperature
) {
}
