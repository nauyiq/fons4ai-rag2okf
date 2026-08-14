package com.fons.cloud.ai.rag2okf.common.request.user;

import jakarta.validation.constraints.NotBlank;

/**
 * 替换 Provider 连接 API Key 的请求。
 *
 * @param apiKey 新的 API Key 明文，加密后存储且不会回显
 * @author hongqy
 */
public record ReplaceApiKeyRequest(
        @NotBlank String apiKey
) {
}
