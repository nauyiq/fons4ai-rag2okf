package com.fons.cloud.ai.rag2okf.common.model.user;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户域返回给文档域的模型能力调用结果。
 *
 * <p>只包含能力语义输出，不携带任何厂商专属字段、API Key、连接 URL 或凭证；
 * 调用方依据本结果继续解析、分块或发布，不感知底层协议。</p>
 *
 * <h3>字段语义</h3>
 * <ul>
 *   <li>{@code content} — 文本输出；LLM 回答、ASR 转写文本、VLM/OCR 识别文本</li>
 *   <li>{@code structuredData} — 结构化输出；原生 JSON Schema 路径或提示词路径解析后的对象</li>
 *   <li>{@code embeddings} — 向量列表；仅 EMBEDDING 能力使用，每条向量维度一致</li>
 *   <li>{@code usage} — 调用计量；promptTokens/completionTokens/totalTokens</li>
 *   <li>{@code traceId} — 外部 traceId；用于关联模型调用日志，不携带明文凭据</li>
 *   <li>{@code warnings} — 调用警告列表；如结构输出回退到提示词路径</li>
 * </ul>
 *
 * <p>安全约束：本对象不得包含明文 API Key、连接 URL 或模型响应原文（content 除外）；
 * usage 只包含 token 计量，不包含计费信息；traceId 只用于日志关联，不携带凭证。</p>
 *
 * @param content        文本输出，可空（纯 EMBEDDING 调用）
 * @param structuredData 结构化输出，可空
 * @param embeddings     向量列表，可空（仅 EMBEDDING）
 * @param usage          调用计量，可空
 * @param traceId        外部 traceId，可空
 * @param warnings       调用警告列表，可空
 * @author hongqy
 */
public record ModelCapabilityResult(
    String content,
    Map<String, Object> structuredData,
    List<float[]> embeddings,
    Usage usage,
    String traceId,
    List<String> warnings
) {

    /** 紧凑构造器，防御性拷贝并拒绝空值。 */
    public ModelCapabilityResult {
        embeddings = embeddings == null ? null : List.copyOf(embeddings);
        structuredData = structuredData == null ? null : Map.copyOf(structuredData);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * 创建文本结果。
     *
     * @param content 文本输出
     * @param usage   调用计量
     * @param traceId 外部 traceId
     * @return 文本型结果
     */
    public static ModelCapabilityResult text(String content, Usage usage, String traceId) {
        return new ModelCapabilityResult(content, null, null, usage, traceId, List.of());
    }

    /**
     * 创建结构化结果。
     *
     * @param content        文本输出（可为空）
     * @param structuredData 结构化输出
     * @param usage          调用计量
     * @param traceId        外部 traceId
     * @param warnings       调用警告
     * @return 结构化结果
     */
    public static ModelCapabilityResult structured(String content, Map<String, Object> structuredData,
                                                     Usage usage, String traceId, List<String> warnings) {
        return new ModelCapabilityResult(content, structuredData, null, usage, traceId, warnings);
    }

    /**
     * 创建向量结果。
     *
     * @param embeddings 向量列表
     * @param usage      调用计量
     * @param traceId    外部 traceId
     * @return 向量型结果
     */
    public static ModelCapabilityResult embedding(List<float[]> embeddings, Usage usage, String traceId) {
        return new ModelCapabilityResult(null, null, embeddings, usage, traceId, List.of());
    }

    /**
     * 调用计量。
     *
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     * @param totalTokens      总 token 数
     */
    public record Usage(long promptTokens, long completionTokens, long totalTokens) {
        public Usage {
            Objects.requireNonNull(promptTokens, "promptTokens must not be null");
            Objects.requireNonNull(completionTokens, "completionTokens must not be null");
            Objects.requireNonNull(totalTokens, "totalTokens must not be null");
        }
    }
}
