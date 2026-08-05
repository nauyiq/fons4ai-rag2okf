package com.fons.cloud.ai.rag2okf.common.request;

import com.fons.cloud.ai.rag2okf.common.constants.ModelProviderTemplate;

/**
 * 创建用户 Provider 连接的请求。
 *
 * @param templateCode 内置模板或 CUSTOM
 * @param providerName 用户可识别的厂商名称
 * @param displayName 当前用户下唯一的连接展示名称
 * @param baseUrl 模型 API 根地址，必须为安全的 HTTPS 公网地址
 * @param apiKey 仅创建或显式替换时提交的 API Key，不会回显
 * @author hongqy
 */
public record CreateModelConnectionRequest(
        ModelProviderTemplate templateCode,
        String providerName,
        String displayName,
        String baseUrl,
        String apiKey
) {
}
