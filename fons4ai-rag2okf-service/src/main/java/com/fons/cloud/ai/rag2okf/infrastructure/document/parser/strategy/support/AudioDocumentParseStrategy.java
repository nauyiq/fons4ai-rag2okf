package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentFileCategory;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedMedia;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTraceStep;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParseStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 音频文件的媒体事实提取策略，具体转写由能力规划器处理。
 *
 * @author hongqy
 */
@Component
public class AudioDocumentParseStrategy implements DocumentParseStrategy {

    @Override
    public boolean supports(DocumentFileCapability capability) {
        return DocumentFileCategory.AUDIO.matches(capability.category());
    }

    @Override
    public RawParseResult parse(ParseExecutionContext context, DocumentFileCapability capability) {
        ParsedMedia media = new ParsedMedia(context.contentType(), null, null, null);
        RawParseBlock block = new RawParseBlock(ParsedBlock.AUDIO_TRANSCRIPT, null, null, null, media,
                SourceAnchor.none(), Map.of("source", "original"));
        return new RawParseResult(null, null, null, null, List.of(block),
                List.of(SourceAnchor.SOURCE_ANCHOR_UNAVAILABLE_WARNING), List.of(new ParseTraceStep(
                        "AUDIO_MEDIA_EXTRACTION", null, null, ParseTraceStep.STATUS_SUCCESS,
                        ParseTraceStep.UNKNOWN_DURATION_MILLIS, null)));
    }
}
