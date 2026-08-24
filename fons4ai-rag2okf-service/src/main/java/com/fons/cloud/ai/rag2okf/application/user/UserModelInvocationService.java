package com.fons.cloud.ai.rag2okf.application.user;

import com.fons.cloud.ai.rag2okf.common.model.user.ModelCapabilityRequest;
import com.fons.cloud.ai.rag2okf.common.model.user.ModelCapabilityResult;

/**
 * 用户域模型能力统一调用入口。
 *
 * <p>文档域按 LLM/EMBEDDING/ASR/VLM/OCR 能力语义提出请求，由本服务解析档案、
 * 解密凭据、调用协议 Client 并返回统一结果；不为每个厂商重复开发适配器。</p>
 *
 * <h3>能力路由</h3>
 * <ul>
 *   <li>LLM — 多模态聊天/结构输出协议；支持原生 JSON Schema → JSON Object → 提示词</li>
 *   <li>EMBEDDING — 向量协议；返回归一化维度向量</li>
 *   <li>ASR — 转写协议（本期未实现，返回 MODEL_CAPABILITY_PROTOCOL_UNSUPPORTED）</li>
 *   <li>VLM — 多模态聊天协议（本期未实现，返回 MODEL_CAPABILITY_PROTOCOL_UNSUPPORTED）</li>
 *   <li>OCR — 多模态聊天协议（本期未实现，返回 MODEL_CAPABILITY_PROTOCOL_UNSUPPORTED）</li>
 *   <li>RERANK — 重排序协议（本期保留，不进入必需步骤）</li>
 * </ul>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>明文 API Key 只在当前调用栈暂存，不进入日志、响应或 Artifact</li>
 *   <li>错误响应只包含错误码、安全摘要和 traceId，不拼接模型响应全文</li>
 *   <li>某连接协议不支持所需能力时返回 MODEL_CAPABILITY_PROTOCOL_UNSUPPORTED</li>
 *   <li>调用失败按分类有界重试或 fail-closed，EMBEDDING 缺失即发布失败</li>
 * </ul>
 *
 * @author hongqy
 */
public interface UserModelInvocationService {

    /**
     * 按能力语义调用用户模型。
     *
     * @param request 能力调用请求，不携带厂商字段、API Key 或连接 URL
     * @return 能力调用结果，不携带明文凭据或模型响应全文
     * @throws com.fons.cloud.ai.rag2okf.common.exception.user.UserModelInvocationException
     *         当协议不支持、调用失败或能力缺失时
     * @throws com.fons.cloud.ai.rag2okf.common.exception.user.ModelConfigurationException
     *         当档案配置无效或凭证解密失败时
     * @throws com.fons.cloud.ai.rag2okf.common.exception.user.ModelAccessDeniedException
     *         当用户无权访问该档案时
     */
    ModelCapabilityResult invoke(ModelCapabilityRequest request);
}
