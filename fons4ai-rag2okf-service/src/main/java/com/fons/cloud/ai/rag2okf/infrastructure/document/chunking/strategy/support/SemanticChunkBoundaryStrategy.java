package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support;

import com.fons.cloud.ai.rag2okf.application.user.UserModelInvocationService;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import com.fons.cloud.ai.rag2okf.common.model.document.ModelProfileReference;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.common.model.user.ModelCapabilityRequest;
import com.fons.cloud.ai.rag2okf.common.model.user.ModelCapabilityResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.ChunkingExecutionContext;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkBoundaryStrategy;
import com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.ChunkCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 使用用户域 EMBEDDING 能力判断相邻文本边界的策略，失败时严格 fail-closed。
 *
 * @author hongqy
 */
@Component
public class SemanticChunkBoundaryStrategy implements ChunkBoundaryStrategy {

    /** 冻结模型引用中 EMBEDDING 能力对应的键。 */
    private static final String EMBEDDING_CAPABILITY = "EMBEDDING";
    /** 参数键：相邻候选块的最低相似度。 */
    private static final String SEMANTIC_THRESHOLD_PARAMETER = "semanticThreshold";
    /** 缺省相邻相似度阈值。 */
    private static final double DEFAULT_THRESHOLD = 0.72D;

    /** 用户域统一模型调用入口。 */
    private final UserModelInvocationService modelInvocationService;

    /**
     * 注入用户域模型调用服务。
     *
     * @param modelInvocationService 用户域统一模型调用入口
     */
    public SemanticChunkBoundaryStrategy(UserModelInvocationService modelInvocationService) {
        this.modelInvocationService = modelInvocationService;
    }

    @Override
    public ChunkBoundaryType getType() {
        return ChunkBoundaryType.SEMANTIC;
    }

    @Override
    public List<ChunkCandidate> split(ParsedDocument document, ChunkPolicy policy, ChunkingExecutionContext context) {
        ModelProfileReference profile = context.getModelProfileRefs().get(EMBEDDING_CAPABILITY);
        if (profile == null || profile.profileKey() == null || profile.profileKey().isBlank()
                || context.getRequestedBy() == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.SEMANTIC_EMBEDDING_UNAVAILABLE);
        }
        double threshold = resolveThreshold(policy);
        List<ChunkCandidate> candidates = new ArrayList<>();
        ChunkCandidate previous = null;
        float[] previousVector = null;
        for (ParsedBlock block : document.blocks()) {
            String text = contentOf(block);
            if (text == null) {
                continue;
            }
            ChunkCandidate current = new ChunkCandidate(text, List.of(block.blockId()), block.anchor());
            float[] currentVector = invokeEmbedding(profile, context.getRequestedBy(), text);
            if (previous != null && cosine(previousVector, currentVector) >= threshold) {
                current = new ChunkCandidate(previous.content() + "\n" + text,
                        mergeBlockIds(previous, block.blockId()), mergeAnchors(previous, block.anchor()),
                        null, null, null);
                candidates.removeLast();
            }
            candidates.add(current);
            previous = current;
            previousVector = currentVector;
        }
        if (candidates.isEmpty()) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSED_DOCUMENT_INVALID);
        }
        return candidates;
    }

    private float[] invokeEmbedding(ModelProfileReference profile, Long requestedBy, String text) {
        try {
            ModelCapabilityResult result = modelInvocationService.invoke(new ModelCapabilityRequest(
                    ModelType.EMBEDDING, profile.profileKey(), requestedBy, text,
                    List.of(), null, ModelCapabilityRequest.ExecutionPolicy.defaultPolicy(), Map.of()));
            if (result.embeddings() == null || result.embeddings().size() != 1
                    || result.embeddings().getFirst() == null || result.embeddings().getFirst().length == 0) {
                throw new DocumentProcessingException(Rag2OkfResultCode.SEMANTIC_EMBEDDING_UNAVAILABLE);
            }
            return result.embeddings().getFirst();
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.SEMANTIC_EMBEDDING_UNAVAILABLE, exception);
        }
    }

    /**
     * 提取可参与向量计算的规范化文本；空白块不创建语义分块候选。
     *
     * @param block 解析后的原始块
     * @return 去除首尾空白的文本；空白块返回 null
     */
    private String contentOf(ParsedBlock block) {
        return block.text() == null || block.text().isBlank() ? null : block.text().strip();
    }

    private double resolveThreshold(ChunkPolicy policy) {
        Map<String, Object> parameters = policy.parameters() == null ? Map.of() : policy.parameters();
        if (parameters.keySet().stream().anyMatch(key -> !SEMANTIC_THRESHOLD_PARAMETER.equals(key))) {
            throw new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        Object value = parameters.get(SEMANTIC_THRESHOLD_PARAMETER);
        if (value == null) {
            return DEFAULT_THRESHOLD;
        }
        if (!(value instanceof Number number)) {
            throw new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        double threshold = number.doubleValue();
        if (threshold <= 0 || threshold > 1) {
            throw new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
        }
        return threshold;
    }

    private double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new DocumentProcessingException(Rag2OkfResultCode.SEMANTIC_EMBEDDING_UNAVAILABLE);
        }
        double product = 0;
        double leftLength = 0;
        double rightLength = 0;
        for (int index = 0; index < left.length; index++) {
            product += left[index] * right[index];
            leftLength += left[index] * left[index];
            rightLength += right[index] * right[index];
        }
        if (leftLength == 0 || rightLength == 0) {
            throw new DocumentProcessingException(Rag2OkfResultCode.SEMANTIC_EMBEDDING_UNAVAILABLE);
        }
        return product / (Math.sqrt(leftLength) * Math.sqrt(rightLength));
    }

    private List<String> mergeBlockIds(ChunkCandidate previous, String currentBlockId) {
        List<String> result = new ArrayList<>(previous.sourceBlockIds());
        result.add(currentBlockId);
        return result;
    }

    /**
     * 合并相邻语义块已证明的来源锚点，并保持原文顺序去重。
     *
     * @param previous 前一候选块
     * @param currentAnchor 当前原始块锚点
     * @return 合并后的完整来源锚点
     */
    private List<SourceAnchor> mergeAnchors(ChunkCandidate previous, SourceAnchor currentAnchor) {
        LinkedHashSet<SourceAnchor> anchors = new LinkedHashSet<>(previous.anchorRefs());
        anchors.add(currentAnchor);
        return List.copyOf(anchors);
    }
}
