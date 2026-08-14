package com.fons.cloud.ai.rag2okf.common.response.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProviderTemplate;

/**
 * Provider 模板的非敏感预填信息。
 *
 * @param code 模板代码
 * @param providerName 厂商名称
 * @param defaultBaseUrl 常见 API 根地址
 * @param officialUrl 厂商官方网站 URL，用于前端 ProviderCard 官方跳转；CUSTOM 为 null
 * @author hongqy
 */
public record ModelProviderTemplateResponse(
        ModelProviderTemplate code,
        String providerName,
        String defaultBaseUrl,
        String officialUrl
) {
}
