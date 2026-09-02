package com.fons.cloud.ai.rag2okf.application.user;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;
import com.fons.cloud.ai.rag2okf.common.exception.user.UserModelInvocationException;
import com.fons.cloud.ai.rag2okf.common.model.user.EncryptedCredential;
import com.fons.cloud.ai.rag2okf.common.model.user.ModelCapabilityRequest;
import com.fons.cloud.ai.rag2okf.common.model.user.ModelCapabilityResult;
import com.fons.cloud.ai.rag2okf.common.model.user.ResolvedUserModel;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelConnection;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserModelAggregate;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.common.exception.user.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.user.AesGcmCredentialCipher;
import com.fons.cloud.ai.rag2okf.infrastructure.factory.LangChain4jModelClientFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link UserModelInvocationService} 默认实现。
 *
 * <p>按能力路由到对应协议 Client，复用 {@link UserModelAggregate} 解析档案、
 * {@link AesGcmCredentialCipher} 解密凭证、{@link LangChain4jModelClientFactory} 创建 Client。
 * 文档域不重复建设模型档案解析、凭证解密或厂商 Client。</p>
 *
 * <h3>本期能力收敛</h3>
 * <ul>
 *   <li>LLM、EMBEDDING — 已实现协议调用</li>
 *   <li>ASR、VLM、OCR — 本期协议子集未实现，返回 MODEL_CAPABILITY_PROTOCOL_UNSUPPORTED</li>
 *   <li>RERANK、TTS — 本期保留，不进入必需步骤</li>
 * </ul>
 *
 * <h3>安全边界</h3>
 * <ul>
 *   <li>明文 API Key 只在当前调用栈暂存，不进入日志、响应或 Artifact</li>
 *   <li>日志只记录 profileKey、能力、状态和 traceId，不记录 prompt 或全文响应</li>
 *   <li>错误响应只包含错误码和安全摘要，不拼接模型响应全文或连接 URL</li>
 * </ul>
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserModelInvocationService  {

    private final KbModelProfileDomainService modelProfileDomainService;
    private final AesGcmCredentialCipher credentialCipher;
    private final LangChain4jModelClientFactory clientFactory;

    public ModelCapabilityResult invoke(ModelCapabilityRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // 生成调用 traceId，用于关联模型调用日志
        String traceId = generateTraceId();

        // 解析档案和连接，校验所有权和 ACTIVE 状态
        UserModelAggregate aggregate = modelProfileDomainService.findModelAggregateByOwnerUserId(
                request.profileKey(), request.ownerUserId());
        if (aggregate == null) {
            throw new ModelAccessDeniedException();
        }
        ResolvedUserModel resolved = aggregate.requireCallable();

        // 能力路由
        ModelType capability = request.capability();
        return switch (capability.canonical()) {
            case LLM -> invokeLlm(request, resolved, traceId);
            case EMBEDDING -> invokeEmbedding(request, resolved, traceId);
            // 本期未实现 ASR/VLM/OCR 协议子集，标记为不可用
            case ASR, VLM, OCR -> throw protocolUnsupported(capability);
            // RERANK/TTS 本期保留，不进入必需步骤
            case RERANK, TTS -> throw protocolUnsupported(capability);
            default -> throw protocolUnsupported(capability);
        };
    }

    /**
     * 调用 LLM 能力。
     *
     * <p>使用 LangChain4j ChatModel，本期支持纯文本输入；
     * 结构化输出路径（原生 JSON Schema → JSON Object → 提示词）由后续 Task Pack 实现。</p>
     */
    private ModelCapabilityResult invokeLlm(ModelCapabilityRequest request, ResolvedUserModel resolved,
                                             String traceId) {
        validateTextInput(request, ModelType.LLM);

        // 解密凭证：明文 Key 只在当前调用栈暂存
        String apiKey = decryptCredential(resolved);

        try {
            ChatModel chatModel = clientFactory.createChatModel(resolved.descriptor(), apiKey);
            ChatResponse response = chatModel.chat(dev.langchain4j.data.message.UserMessage.from(request.text()));

            String content = response.aiMessage().text();
            ModelCapabilityResult.Usage usage = extractUsage(response);
            log.info("LLM invoke succeeded: profileKey={}, traceId={}, usage={}",
                request.profileKey(), traceId, usage);
            return ModelCapabilityResult.text(content, usage, traceId);
        } catch (RuntimeException exception) {
            throw new UserModelInvocationException(Rag2OkfResultCode.MODEL_CAPABILITY_CALL_FAILED, exception);
        }
    }

    /**
     * 调用 EMBEDDING 能力。
     *
     * <p>使用 LangChain4j EmbeddingModel，返回归一化维度向量。
     * EMBEDDING 是默认发布路径（BM25 + 向量化）的必要能力，缺失即 fail-closed。</p>
     */
    private ModelCapabilityResult invokeEmbedding(ModelCapabilityRequest request, ResolvedUserModel resolved,
                                                   String traceId) {
        validateTextInput(request, ModelType.EMBEDDING);

        String apiKey = decryptCredential(resolved);

        try {
            EmbeddingModel embeddingModel = clientFactory.createEmbeddingModel(resolved.descriptor(), apiKey);
            dev.langchain4j.model.output.Response<Embedding> response =
                embeddingModel.embed(request.text());
            float[] vector = response.content().vector();
            ModelCapabilityResult.Usage usage = new ModelCapabilityResult.Usage(0, 0, 0);
            log.info("EMBEDDING invoke succeeded: profileKey={}, traceId={}, dims={}",
                request.profileKey(), traceId, vector.length);
            return ModelCapabilityResult.embedding(List.of(vector), usage, traceId);
        } catch (dev.langchain4j.exception.TimeoutException exception) {
            throw new UserModelInvocationException(Rag2OkfResultCode.MODEL_CAPABILITY_CALL_FAILED, exception);
        } catch (RuntimeException exception) {
            throw new UserModelInvocationException(Rag2OkfResultCode.MODEL_CAPABILITY_CALL_FAILED, exception);
        }
    }

    /**
     * 解密凭证：明文 Key 只在当前调用栈暂存，不进入日志或响应。
     */
    private String decryptCredential(ResolvedUserModel resolved) {
        KbModelConnection connection = resolved.connection();
        EncryptedCredential credential = new EncryptedCredential(
            connection.getApiKeyCiphertext(),
            connection.getApiKeyNonce(),
            connection.getKeyVersion()
        );
        return credentialCipher.decrypt(credential);
    }

    private void validateTextInput(ModelCapabilityRequest request, ModelType capability) {
        if (request.text() == null || request.text().isBlank()) {
            throw new UserModelInvocationException(Rag2OkfResultCode.MODEL_CAPABILITY_CALL_FAILED);
        }
    }

    private UserModelInvocationException protocolUnsupported(ModelType capability) {
        log.warn("Model capability protocol unsupported: capability={}", capability);
        return new UserModelInvocationException(Rag2OkfResultCode.MODEL_CAPABILITY_PROTOCOL_UNSUPPORTED);
    }

    private ModelCapabilityResult.Usage extractUsage(ChatResponse response) {
        if (response.tokenUsage() == null) {
            return new ModelCapabilityResult.Usage(0, 0, 0);
        }
        var usage = response.tokenUsage();
        return new ModelCapabilityResult.Usage(
            usage.inputTokenCount() != null ? usage.inputTokenCount() : 0,
            usage.outputTokenCount() != null ? usage.outputTokenCount() : 0,
            usage.totalTokenCount() != null ? usage.totalTokenCount() : 0
        );
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}
