package com.fons.cloud.ai.rag2okf.common.response;

import com.fons.cloud.ai.rag2okf.common.constants.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelTestStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelType;

import java.util.Date;

/**
 * 不包含 Provider 凭证的模型档案响应。
 *
 * @param profileKey 模型档案业务标识
 * @param connectionKey 所属 Provider 连接标识
 * @param modelType 档案能力类型
 * @param modelName 厂商实际模型 ID
 * @param dimensions 向量维度提示
 * @param timeoutSeconds 受控超时秒数
 * @param temperature 受控对话温度
 * @param status 档案启用状态
 * @param lastTestStatus 最近测试状态
 * @param lastTestAt 最近测试时间
 * @param contextWindowLength 上下文窗口长度，暂未持久化时为 null
 * @param updated 最近更新时间
 * @author hongqy
 */
public record ModelProfileResponse(
        String profileKey,
        String connectionKey,
        ModelType modelType,
        String modelName,
        Integer dimensions,
        Integer timeoutSeconds,
        Double temperature,
        ModelProfileStatus status,
        ModelTestStatus lastTestStatus,
        Date lastTestAt,
        Integer contextWindowLength,
        Date updated
) {
}
