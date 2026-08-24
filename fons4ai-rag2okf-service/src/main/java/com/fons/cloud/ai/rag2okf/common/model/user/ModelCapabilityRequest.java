package com.fons.cloud.ai.rag2okf.common.model.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文档域向用户域发起的模型能力调用请求。
 *
 * <p>只包含能力语义输入，不携带任何厂商专属字段、API Key、连接 URL 或凭证；
 * 文档域不为每个厂商重复开发适配器，统一通过本对象表达 LLM/EMBEDDING/ASR/VLM/OCR 调用意图。</p>
 *
 * <h3>字段语义</h3>
 * <ul>
 *   <li>{@code capability} — 能力类型，必填；LLM/EMBEDDING/RERANK/ASR/VLM/OCR 之一（RERANK 本期保留）</li>
 *   <li>{@code profileKey} — 用户域模型档案业务标识，必填；任务快照只保存此 key</li>
 *   <li>{@code ownerUserId} — 当前用户主键，必填；用于所有权和归属校验</li>
 *   <li>{@code text} — 文本输入；LLM 提示词、EMBEDDING 待向量化文本；纯媒体调用可空</li>
 *   <li>{@code mediaObjectKeys} — 媒体对象引用（MinIO key）；ASR 音频、VLM/OCR 图片使用</li>
 *   <li>{@code outputSchema} — 结构化输出 JSON Schema；仅 LLM/VLM/OCR 使用，强制本地校验</li>
 *   <li>{@code executionPolicy} — 执行策略；超时、重试、结构输出路径偏好</li>
 *   <li>{@code attributes} — Schema 白名单标量；禁止任意对象透传模型/厂商响应</li>
 * </ul>
 *
 * <p>安全约束：本对象不得包含明文 API Key、连接 URL 或加密载荷；
 * 凭证由用户域在调用栈内安全解析，不返回给调用方。</p>
 *
 * @param capability        能力类型，必填
 * @param profileKey        模型档案业务标识，必填
 * @param ownerUserId       当前用户主键，必填
 * @param text              文本输入，可空
 * @param mediaObjectKeys   媒体对象引用，可空
 * @param outputSchema      结构化输出 JSON Schema，可空
 * @param executionPolicy   执行策略，可空
 * @param attributes        Schema 白名单标量，可空
 * @author hongqy
 */
public record ModelCapabilityRequest(
    ModelType capability,
    String profileKey,
    Long ownerUserId,
    String text,
    List<String> mediaObjectKeys,
    String outputSchema,
    ExecutionPolicy executionPolicy,
    Map<String, Object> attributes
) {

    /** 紧凑构造器，防御性拷贝并拒绝空值。字段约束由 UserModelInvocationService 校验。 */
    public ModelCapabilityRequest {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(profileKey, "profileKey must not be null");
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        mediaObjectKeys = mediaObjectKeys == null ? List.of() : List.copyOf(mediaObjectKeys);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 执行策略，控制调用行为。
     *
     * @param timeoutSeconds       超时秒数；null 使用档案默认值
     * @param maxRetries           最大重试次数；0 表示不重试
     * @param structuredOutputMode 结构输出路径偏好：NATIVE/JSON_OBJECT/PROMPT
     */
    public record ExecutionPolicy(
        Integer timeoutSeconds,
        int maxRetries,
        StructuredOutputMode structuredOutputMode
    ) {
        /** 默认执行策略：30s 超时、不重试、原生 JSON Schema 优先。 */
        public static ExecutionPolicy defaultPolicy() {
            return new ExecutionPolicy(null, 0, StructuredOutputMode.NATIVE);
        }
    }

    /** 结构化输出路径偏好。 */
    public enum StructuredOutputMode {
        /** 原生 JSON Schema（厂商支持时优先）。 */
        NATIVE,
        /** JSON Object 模式。 */
        JSON_OBJECT,
        /** 提示词引导 JSON 输出。 */
        PROMPT
    }
}
