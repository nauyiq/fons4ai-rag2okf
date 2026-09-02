package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedMedia;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.BuiltInCapabilityPlanner;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.BuiltInFileCapabilityCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class BuiltInCapabilityPlannerTest {

    @Test
    void shouldFailClosedWhenRequiredOcrProfileIsMissing() {
        UserModelInvocationService service = mock(UserModelInvocationService.class);
        BuiltInCapabilityPlanner planner = new BuiltInCapabilityPlanner(service);
        DocumentFileCapability capability = new BuiltInFileCapabilityCatalog()
                .require("image.png", "image/png");
        RawParseResult raw = new RawParseResult(null, null, null, null,
                List.of(new RawParseBlock(
                        ParsedBlock.IMAGE, null, null, null,
                        new ParsedMedia("image/png", null, null, null),
                        SourceAnchor.none(), Map.of())),
                List.of("SOURCE_ANCHOR_UNAVAILABLE"), List.of());

        assertThatThrownBy(() -> planner.enhance(
                BuiltInDocumentParserTest.context("image.png", "image/png"), capability, raw))
                .isInstanceOf(DocumentProcessingException.class)
                .extracting("code").isEqualTo(Rag2OkfResultCode.MODEL_CAPABILITY_MISSING.getCode());
        verifyNoInteractions(service);
    }

    @Test
    void shouldKeepBaseResultAndWarnWhenEnhancementProfileIsMissing() {
        UserModelInvocationService service = mock(UserModelInvocationService.class);
        BuiltInCapabilityPlanner planner = new BuiltInCapabilityPlanner(service);
        DocumentFileCapability capability = new BuiltInFileCapabilityCatalog()
                .require("sample.txt", "text/plain");
        RawParseResult raw = new RawParseResult(null, null, null, null,
                List.of(new RawParseBlock(
                        ParsedBlock.PARAGRAPH, "hello", null, null, null,
                        SourceAnchor.none(), Map.of())),
                List.of("SOURCE_ANCHOR_UNAVAILABLE"), List.of());

        RawParseResult result = planner.enhance(
                BuiltInDocumentParserTest.context("sample.txt", "text/plain"), capability, raw);

        assertThat(result.blocks().getFirst().text()).isEqualTo("hello");
        assertThat(result.warnings()).contains("MODEL_ENHANCEMENT_UNAVAILABLE:LLM");
        verifyNoInteractions(service);
    }
}
