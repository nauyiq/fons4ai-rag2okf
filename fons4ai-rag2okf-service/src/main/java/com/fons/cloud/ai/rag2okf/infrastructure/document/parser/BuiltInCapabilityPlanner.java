package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.application.user.UserModelInvocationService;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ModelProfileReference;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedMedia;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTraceStep;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import com.fons.cloud.ai.rag2okf.common.model.user.ModelCapabilityRequest;
import com.fons.cloud.ai.rag2okf.common.model.user.ModelCapabilityResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Built-in 必要/增强模型能力规划器。
 *
 * <p>必要能力缺失或失败时 fail-closed；增强能力缺失或失败时保留基础结果并写入
 * 安全 warning。档案只通过快照 profileKey 引用，文档域不读取凭据。</p>
 */
@Component
public class BuiltInCapabilityPlanner {

    private final UserModelInvocationService invocationService;

    public BuiltInCapabilityPlanner(UserModelInvocationService invocationService) {
        this.invocationService = invocationService;
    }

    public RawParseResult enhance(
            ParseExecutionContext context,
            DocumentFileCapability capability,
            RawParseResult baseResult) {
        Set<String> required = determineRequired(capability, baseResult);
        List<RawParseBlock> blocks = new ArrayList<>(baseResult.blocks());
        LinkedHashSet<String> warnings = new LinkedHashSet<>(baseResult.warnings());
        List<ParseTraceStep> trace = new ArrayList<>(baseResult.trace());

        for (String name : required) {
            ModelCapabilityResult result = invoke(context, name, true, warnings, trace);
            applyRequiredOutput(blocks, name, result);
        }
        for (String name : capability.enhancementCapabilities()) {
            ModelCapabilityResult result = invoke(context, name, false, warnings, trace);
            if (result != null && result.warnings() != null) {
                warnings.addAll(result.warnings());
            }
        }
        return baseResult.withBlocksAndEvidence(blocks, List.copyOf(warnings), trace);
    }

    private Set<String> determineRequired(
            DocumentFileCapability capability, RawParseResult result) {
        String extension = capability.extension().toLowerCase(Locale.ROOT);
        if (Set.of("png", "jpg", "jpeg", "webp").contains(extension)
                && result.blocks().stream().noneMatch(block -> ParsedBlock.PAGE_MARKDOWN.equals(block.type()))) {
            return Set.of("OCR");
        }
        if (Set.of("wav", "mp3", "m4a").contains(extension)) {
            return Set.of("ASR");
        }
        boolean hasText = result.blocks().stream()
                .anyMatch(block -> block.text() != null && !block.text().isBlank());
        if (!hasText && Set.of("pdf", "docx").contains(extension)) {
            return Set.of("OCR");
        }
        return Set.of();
    }

    private ModelCapabilityResult invoke(
            ParseExecutionContext context, String capabilityName, boolean required,
            Set<String> warnings, List<ParseTraceStep> trace) {
        ModelProfileReference ref = findRef(context.modelProfileRefs(), capabilityName);
        if (ref == null || ref.profileKey() == null || ref.profileKey().isBlank()) {
            if (required) {
                throw new DocumentProcessingException(Rag2OkfResultCode.MODEL_CAPABILITY_MISSING);
            }
            warnings.add("MODEL_ENHANCEMENT_UNAVAILABLE:" + capabilityName);
            trace.add(trace(capabilityName, null, "WARNING", null));
            return null;
        }
        try {
            ModelType type = ModelType.valueOf(capabilityName);
            ModelCapabilityResult result = invocationService.invoke(new ModelCapabilityRequest(
                    type, ref.profileKey(), context.ownerUserId(), baseText(context),
                    List.of(context.sourceObjectKey()), null,
                    ModelCapabilityRequest.ExecutionPolicy.defaultPolicy(), Map.of()));
            trace.add(trace(capabilityName, ref.profileKey(), "SUCCESS",
                    result == null ? null : result.traceId()));
            return result;
        } catch (RuntimeException exception) {
            if (required) {
                throw new DocumentProcessingException(
                        Rag2OkfResultCode.MODEL_CAPABILITY_CALL_FAILED, exception);
            }
            warnings.add("MODEL_ENHANCEMENT_FAILED:" + capabilityName);
            trace.add(trace(capabilityName, ref.profileKey(), "WARNING", null));
            return null;
        }
    }

    private void applyRequiredOutput(
            List<RawParseBlock> blocks,
            String capability,
            ModelCapabilityResult result) {
        String content = result == null ? null : result.content();
        if (content == null || content.isBlank()) {
            throw new DocumentProcessingException(Rag2OkfResultCode.MODEL_CAPABILITY_CALL_FAILED);
        }
        for (int index = 0; index < blocks.size(); index++) {
            RawParseBlock block = blocks.get(index);
            if (block.text() == null || block.text().isBlank()) {
                if (ParsedBlock.IMAGE.equals(block.type())) {
                    ParsedMedia media = new ParsedMedia(
                            block.media().mediaType(), block.media().objectKey(),
                            content, content);
                    blocks.set(index, new RawParseBlock(
                            block.type(), null, block.level(), block.table(), media,
                            block.anchor(), block.attributes()));
                } else {
                    blocks.set(index, block.withText(content));
                }
                return;
            }
        }
    }

    private ModelProfileReference findRef(
            Map<String, ModelProfileReference> refs, String capability) {
        ModelProfileReference direct = refs.get(capability);
        if (direct != null) {
            return direct;
        }
        return refs.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(capability))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String baseText(ParseExecutionContext context) {
        return "对当前文档执行受控能力增强，文档标识：" + context.documentKey();
    }

    private ParseTraceStep trace(
            String capability, String profileKey, String status, String traceId) {
        return new ParseTraceStep(
                "MODEL_" + capability, capability, profileKey, status, 0, traceId);
    }
}
