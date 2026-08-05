package com.fons.cloud.ai.rag2okf.application.publication;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.application.model.CredentialCipher;
import com.fons.cloud.ai.rag2okf.application.model.EncryptedCredential;
import com.fons.cloud.ai.rag2okf.application.model.ModelClientFactory;
import com.fons.cloud.ai.rag2okf.application.model.ResolvedModelDescriptor;
import com.fons.cloud.ai.rag2okf.application.model.ResolvedUserModel;
import com.fons.cloud.ai.rag2okf.application.model.UserModelResolver;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelUsageType;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelBindingEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;
import com.fons.cloud.ai.rag2okf.domain.publication.PublicationProjectionPort.ChunkProjection;
import com.fons.cloud.ai.rag2okf.domain.service.KbKnowledgeBaseDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelBindingDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布时同步向量化分块投影（CR-013，D-007）。
 *
 * <p>从知识库 EMBEDDING 绑定解析用户配置的模型，使用 {@link ModelClientFactory}
 * 创建动态 EmbeddingModel 客户端，批量计算分块向量并填充到 {@link ChunkProjection}。
 *
 * <p>设计约束：
 * <ul>
 *   <li>不注册全局 EmbeddingModel Bean；每次发布按用户配置动态创建。</li>
 *   <li>apiKey 仅在调用栈暂存，不持久化到日志、Redis 或 MySQL。</li>
 *   <li>无 EMBEDDING 绑定时降级为 BM25-only（vector 全为 null）。</li>
 *   <li>向量化失败阻塞发布：维度不匹配为 FatalFailure，其他失败为 RetryableFailure。</li>
 * </ul>
 *
 * @author hongqy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingProjectionService {

    /** 错误码：知识库不存在。 */
    public static final String ERR_KB_NOT_FOUND = "EMBEDDING_KB_NOT_FOUND";
    /** 错误码：模型档案不可用。 */
    public static final String ERR_PROFILE_UNAVAILABLE = "EMBEDDING_PROFILE_UNAVAILABLE";
    /** 错误码：向量维度不匹配（FatalFailure）。 */
    public static final String ERR_DIMS_MISMATCH = "EMBEDDING_DIMS_MISMATCH";
    /** 错误码：模型调用失败（RetryableFailure）。 */
    public static final String ERR_MODEL_INVOCATION = "EMBEDDING_MODEL_ERROR";

    private final KbKnowledgeBaseDomainService knowledgeBaseDomainService;
    private final KbModelBindingDomainService modelBindingDomainService;
    private final KbModelProfileDomainService modelProfileDomainService;
    private final UserModelResolver userModelResolver;
    private final CredentialCipher credentialCipher;
    private final ModelClientFactory modelClientFactory;

    @Value("${sys.embedding.dims:1024}")
    private int embeddingDims;

    /**
     * 发布时同步向量化分块投影。
     *
     * <p>无 EMBEDDING 绑定时返回原列表（vector 全为 null，降级 BM25-only）。
     * 向量化失败抛出 {@link EmbeddingException}，阻塞发布。
     *
     * @param knowledgeBaseKey 知识库业务标识
     * @param projections      待向量化的分块投影列表
     * @return 带 vector 的新分块投影列表
     * @throws EmbeddingException 向量化失败
     */
    public List<ChunkProjection> embedProjections(String knowledgeBaseKey, List<ChunkProjection> projections) {
        KbKnowledgeBaseEntity kb = knowledgeBaseDomainService.getOne(
                Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                        .eq(KbKnowledgeBaseEntity::getKnowledgeBaseKey, knowledgeBaseKey));
        if (kb == null) {
            throw new EmbeddingException(ERR_KB_NOT_FOUND,
                    "知识库不存在: " + knowledgeBaseKey, true);
        }

        KbModelBindingEntity binding = modelBindingDomainService.getOne(
                Wrappers.<KbModelBindingEntity>lambdaQuery()
                        .eq(KbModelBindingEntity::getKnowledgeBaseId, kb.getId())
                        .eq(KbModelBindingEntity::getUsageType, ModelUsageType.EMBEDDING));

        // 无 EMBEDDING 绑定 -> 降级 BM25-only
        if (binding == null) {
            log.info("No EMBEDDING binding, skip vectorization: knowledgeBaseKey={}", knowledgeBaseKey);
            return projections;
        }

        KbModelProfileEntity profile = modelProfileDomainService.getById(binding.getModelProfileId());
        if (profile == null || profile.getStatus() != ModelProfileStatus.ACTIVE) {
            throw new EmbeddingException(ERR_PROFILE_UNAVAILABLE,
                    "向量化模型档案不可用", true);
        }

        ResolvedUserModel resolvedModel = userModelResolver.resolveOwnedActiveProfile(
                profile.getProfileKey(), profile.getOwnerUserId());

        String apiKey = credentialCipher.decrypt(new EncryptedCredential(
                resolvedModel.connection().getApiKeyCiphertext(),
                resolvedModel.connection().getApiKeyNonce(),
                resolvedModel.connection().getKeyVersion()));

        ResolvedModelDescriptor descriptor = resolvedModel.descriptor();
        EmbeddingModel embeddingModel = modelClientFactory.createEmbeddingModel(descriptor, apiKey);

        return embedChunks(projections, embeddingModel, knowledgeBaseKey);
    }

    private List<ChunkProjection> embedChunks(List<ChunkProjection> projections,
                                              EmbeddingModel embeddingModel,
                                              String knowledgeBaseKey) {
        // 收集需要向量化的 chunk（embeddingText != null）
        List<ChunkProjection> toEmbed = new ArrayList<>();
        for (ChunkProjection p : projections) {
            if (p.embeddingText() != null) {
                toEmbed.add(p);
            }
        }

        if (toEmbed.isEmpty()) {
            log.info("All chunks skip embedding, vectorization skipped: knowledgeBaseKey={}", knowledgeBaseKey);
            return projections;
        }

        List<TextSegment> segments = new ArrayList<>(toEmbed.size());
        for (ChunkProjection p : toEmbed) {
            segments.add(TextSegment.from(p.embeddingText()));
        }

        Response<List<Embedding>> response;
        try {
            response = embeddingModel.embedAll(segments);
        } catch (RuntimeException e) {
            log.warn("Embedding model invocation failed: knowledgeBaseKey={}, error={}",
                    knowledgeBaseKey, e.getClass().getSimpleName());
            throw new EmbeddingException(ERR_MODEL_INVOCATION,
                    "向量化模型调用失败: " + e.getClass().getSimpleName(), false, e);
        }

        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new EmbeddingException(ERR_MODEL_INVOCATION,
                    "向量化模型返回空结果", false);
        }

        List<Embedding> embeddings = response.content();

        // 维度校验（FatalFailure）
        int actualDims = embeddings.get(0).vector().length;
        if (actualDims != embeddingDims) {
            log.error("Embedding dims mismatch: expected={}, actual={}, knowledgeBaseKey={}",
                    embeddingDims, actualDims, knowledgeBaseKey);
            throw new EmbeddingException(ERR_DIMS_MISMATCH,
                    "向量维度不匹配: expected=" + embeddingDims + ", actual=" + actualDims, true);
        }

        // 填充 vector
        Map<String, float[]> vectorMap = new HashMap<>();
        for (int i = 0; i < toEmbed.size(); i++) {
            vectorMap.put(toEmbed.get(i).chunkKey(), embeddings.get(i).vector());
        }

        List<ChunkProjection> result = new ArrayList<>(projections.size());
        for (ChunkProjection p : projections) {
            float[] vector = vectorMap.get(p.chunkKey());
            result.add(new ChunkProjection(
                    p.chunkKey(), p.parentChunkKey(), p.chunkLevel(), p.ordinal(),
                    p.rawText(), p.displayText(), p.embeddingText(),
                    p.titlePath(), p.sourceLocatorType(), p.pageNumber(),
                    p.startOffset(), p.endOffset(), vector));
        }

        log.info("Embedding completed: knowledgeBaseKey={}, total={}, embedded={}",
                knowledgeBaseKey, projections.size(), toEmbed.size());
        return result;
    }

    /**
     * 向量化异常，携带安全化错误码和 fatal 标志。
     *
     * <p>fatal=true 表示不可重试（如维度不匹配），fatal=false 表示可重试（如模型临时故障）。
     */
    public static class EmbeddingException extends RuntimeException {

        private final String errorCode;
        private final boolean fatal;

        public EmbeddingException(String errorCode, String message, boolean fatal) {
            super(message);
            this.errorCode = errorCode;
            this.fatal = fatal;
        }

        public EmbeddingException(String errorCode, String message, boolean fatal, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
            this.fatal = fatal;
        }

        public String errorCode() {
            return errorCode;
        }

        public boolean fatal() {
            return fatal;
        }
    }
}
