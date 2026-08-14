package com.fons.cloud.ai.rag2okf.common.response.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelTestStatus;

/**
 * 模型能力测试的安全化结果。
 *
 * @param status 测试成功或失败状态
 * @param errorCode 非敏感错误码；成功时为空
 * @param dimensions 实际向量维度；仅向量测试成功时可能返回
 * @author hongqy
 */
public record ModelTestResponse(ModelTestStatus status, String errorCode, Integer dimensions) {
}
