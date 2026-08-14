package com.fons.cloud.ai.rag2okf.common.response.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProtocolType;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelTestStatus;

import java.util.Date;

/**
 * 不包含 API Key 明文、密文和 nonce 的 Provider 连接响应。
 *
 * @param connectionKey Provider 连接业务标识
 * @param providerCode 模板代码
 * @param providerName 用户可见厂商名称
 * @param displayName 用户定义的连接名称
 * @param protocolType 调用协议
 * @param baseUrl 经安全校验后保存的 API 根地址
 * @param apiKeyMask 不可逆 Key 掩码
 * @param status 连接启用状态
 * @param lastTestStatus 最近测试状态
 * @param lastTestAt 最近测试时间
 * @param apiKeyConfigured 是否已配置 API Key（apiKeyMask 非空时为 true）
 * @param updated 最近更新时间
 * @author hongqy
 */
public record ModelConnectionResponse(
        String connectionKey,
        String providerCode,
        String providerName,
        String displayName,
        ModelProtocolType protocolType,
        String baseUrl,
        String apiKeyMask,
        ModelConnectionStatus status,
        ModelTestStatus lastTestStatus,
        Date lastTestAt,
        Boolean apiKeyConfigured,
        Date updated
) {
}
