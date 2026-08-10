package com.fons.cloud.ai.rag2okf.common.constants;

/**
 * P0 内置的 OpenAI-compatible Provider 模板。
 *
 * <p>模板仅预填厂商信息、常见地址和官方跳转链接，用户仍需提供可用的模型名称与 API Key。
 * 模型名称由用户手填，服务端不维护模型清单。</p>
 *
 * @author hongqy
 */
public enum ModelProviderTemplate {
    /** 阿里云百炼（通义千问）。 */
    ALIYUN_DASHSCOPE("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "https://dashscope.aliyun.com"),
    /** DeepSeek。 */
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "https://platform.deepseek.com"),
    /** OpenAI。 */
    OPENAI("OpenAI", "https://api.openai.com/v1", "https://platform.openai.com"),
    /** 火山方舟。 */
    VOLCENGINE_ARK("火山方舟", "https://ark.cn-beijing.volces.com/api/v3", "https://www.volcengine.com/product/ark"),
    /** 腾讯混元。 */
    TENCENT_HUNYUAN("腾讯混元", "https://api.hunyuan.cloud.tencent.com/v1", "https://hunyuan.tencent.com"),
    /** 智谱 BigModel。 */
    ZHIPU_BIGMODEL("智谱 BigModel", "https://open.bigmodel.cn/api/paas/v4", "https://open.bigmodel.cn"),
    /** 用户自定义 OpenAI-compatible 服务。 */
    CUSTOM("自定义", null, null);

    private final String providerName;
    private final String defaultBaseUrl;
    private final String officialUrl;

    ModelProviderTemplate(String providerName, String defaultBaseUrl, String officialUrl) {
        this.providerName = providerName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.officialUrl = officialUrl;
    }

    /** @return 用户可见的厂商名称 */
    public String getProviderName() {
        return providerName;
    }

    /** @return 常见 API 根地址；CUSTOM 不提供默认值 */
    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    /** @return 厂商官方网站 URL，用于前端 ProviderCard 官方跳转；CUSTOM 为 null */
    public String getOfficialUrl() {
        return officialUrl;
    }
}
