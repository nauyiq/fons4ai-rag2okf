package com.fons.cloud.ai.rag2okf.common.constants;

/**
 * P0 内置的 OpenAI-compatible Provider 模板。
 *
 * <p>模板仅预填厂商信息和常见地址，用户仍需提供可用的模型名称与 API Key。</p>
 *
 * @author hongqy
 */
public enum ModelProviderTemplate {
    /** 阿里云百炼。 */
    ALIYUN_DASHSCOPE("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    /** 火山方舟。 */
    VOLCENGINE_ARK("火山方舟", "https://ark.cn-beijing.volces.com/api/v3"),
    /** 腾讯混元。 */
    TENCENT_HUNYUAN("腾讯混元", "https://api.hunyuan.cloud.tencent.com/v1"),
    /** 智谱 BigModel。 */
    ZHIPU_BIGMODEL("智谱 BigModel", "https://open.bigmodel.cn/api/paas/v4"),
    /** 用户自定义 OpenAI-compatible 服务。 */
    CUSTOM("自定义", null);

    private final String providerName;
    private final String defaultBaseUrl;

    ModelProviderTemplate(String providerName, String defaultBaseUrl) {
        this.providerName = providerName;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    /** @return 用户可见的厂商名称 */
    public String getProviderName() {
        return providerName;
    }

    /** @return 常见 API 根地址；CUSTOM 不提供默认值 */
    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }
}
