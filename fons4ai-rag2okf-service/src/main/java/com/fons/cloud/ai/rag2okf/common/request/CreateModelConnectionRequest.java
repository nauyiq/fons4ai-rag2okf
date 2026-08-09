package com.fons.cloud.ai.rag2okf.common.request;

/**
 * 创建用户 Provider 连接的请求。
 *
 * @param providerCode 厂商代码，对应 ModelProviderTemplate 名称（如 ALIYUN_DASHSCOPE）或自定义代码
 * @param providerName 用户可识别的厂商名称
 * @param displayName 当前用户下唯一的连接展示名称
 * @param baseUrl 模型 API 根地址，必须为安全的 HTTPS 公网地址
 * @param apiKey 仅创建或显式替换时提交的 API Key，不会回显
 * @author hongqy
 */
public record CreateModelConnectionRequest(
        String providerCode,
        String providerName,
        String displayName,
        String baseUrl,
        String apiKey
) {
}
