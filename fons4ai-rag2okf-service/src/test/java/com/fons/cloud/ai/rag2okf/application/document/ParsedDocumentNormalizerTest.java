package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.ParsedDocumentCodec;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.ParsedDocumentNormalizer;
import com.fons.cloud.ai.rag2okf.common.utils.ParsedDocumentValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParsedDocumentNormalizerTest {

    @Test
    void shouldProduceStableValidatedJson() {
        RawParseResult raw = new RawParseResult("sample", "zh-CN", null, null,
                List.of(new RawParseBlock(
                        ParsedBlock.PARAGRAPH, "hello", null, null, null,
                        SourceAnchor.none(), Map.of())),
                List.of("SOURCE_ANCHOR_UNAVAILABLE"), List.of());
        ParsedDocumentNormalizer normalizer = new ParsedDocumentNormalizer();
        ParsedDocument first = normalizer.normalize(
                BuiltInDocumentParserTest.context("sample.txt", "text/plain"), raw);
        ParsedDocument second = normalizer.normalize(
                BuiltInDocumentParserTest.context("sample.txt", "text/plain"), raw);
        ParsedDocumentCodec codec = new ParsedDocumentCodec();

        ParsedDocumentValidator.validate(first);
        assertThat(first.blocks().getFirst().blockId())
                .isEqualTo(second.blocks().getFirst().blockId());
        assertThat(codec.writeJson(first)).isEqualTo(codec.writeJson(second));
        assertThat(codec.readJson(codec.writeJson(first))).isEqualTo(first);
    }

    @Test
    void shouldKeepOfficialOcrPageAnchorAndImageUrls() {
        RawParseResult raw = new RawParseResult("sample", "zh-CN", 2, null,
                List.of(new RawParseBlock(
                        ParsedBlock.PAGE_MARKDOWN, "# 第 2 页", null, null, null,
                        SourceAnchor.page(2), Map.of("ocrEngine", "paddleocr-official"),
                        Map.of("images/chart.png", "https://image.example/chart.png"),
                        Map.of("layout", "https://image.example/layout.jpg"))),
                List.of(), List.of());

        ParsedDocument parsed = new ParsedDocumentNormalizer().normalize(
                BuiltInDocumentParserTest.context("sample.pdf", "application/pdf"), raw);

        ParsedDocumentValidator.validate(parsed);
        assertThat(parsed.blocks()).singleElement().satisfies(block -> {
            assertThat(block.type()).isEqualTo(ParsedBlock.PAGE_MARKDOWN);
            assertThat(block.anchor().page()).isEqualTo(2);
            assertThat(block.markdownImages()).containsEntry(
                    "images/chart.png", "https://image.example/chart.png");
            assertThat(block.outputImages()).containsEntry(
                    "layout", "https://image.example/layout.jpg");
        });
    }
}
