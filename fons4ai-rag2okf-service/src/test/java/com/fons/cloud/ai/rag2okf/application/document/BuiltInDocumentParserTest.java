package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.BuiltInCapabilityPlanner;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support.BuiltInDocumentParser;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.BuiltInFileCapabilityCatalog;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParseStrategy;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support.DocumentParseStrategyRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuiltInDocumentParserTest {

    @Test
    void shouldUseFrozenBuiltInPipelineWithoutFallback() {
        BuiltInFileCapabilityCatalog catalog = new BuiltInFileCapabilityCatalog();
        BuiltInCapabilityPlanner planner = mock(BuiltInCapabilityPlanner.class);
        DocumentParseStrategy strategy = mock(DocumentParseStrategy.class);
        DocumentParseStrategyRegistry strategyRegistry = new DocumentParseStrategyRegistry(List.of(strategy));
        ParseExecutionContext context = context("sample.txt", "text/plain");
        RawParseResult raw = new RawParseResult(null, null, null, null,
                List.of(new RawParseBlock(
                        ParsedBlock.PARAGRAPH, "hello", null, null, null,
                        SourceAnchor.none(), Map.of())),
                List.of("SOURCE_ANCHOR_UNAVAILABLE"), List.of());
        when(strategy.supports(catalog.require("sample.txt", "text/plain"))).thenReturn(true);
        when(strategy.parse(context, catalog.require("sample.txt", "text/plain"))).thenReturn(raw);
        when(planner.enhance(context, catalog.require("sample.txt", "text/plain"), raw))
                .thenReturn(raw);

        RawParseResult result = new BuiltInDocumentParser(catalog, strategyRegistry, planner).parse(context);

        assertThat(result.blocks()).singleElement()
                .extracting(RawParseBlock::text).isEqualTo("hello");
    }

    static ParseExecutionContext context(String filename, String contentType) {
        return new ParseExecutionContext(
                "01ARZ3NDEKTSV4RRFFQ69G5FAV", "01ARZ3NDEKTSV4RRFFQ69G5FAW",
                "01ARZ3NDEKTSV4RRFFQ69G5FAX", "01ARZ3NDEKTSV4RRFFQ69G5FAY",
                1, 1L, "01ARZ3NDEKTSV4RRFFQ69G5FAZ",
                "workspaces/source", filename, contentType, "abc",
                ParserType.BUILT_IN, Map.of(), () -> new ByteArrayInputStream(new byte[]{1}));
    }
}
