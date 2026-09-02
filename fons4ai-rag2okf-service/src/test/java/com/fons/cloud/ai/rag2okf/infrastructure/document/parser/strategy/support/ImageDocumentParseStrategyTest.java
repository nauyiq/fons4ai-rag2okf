package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrDocumentResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrGateway;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrPageResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 图片策略到 official OCR 页事实的映射测试。 */
class ImageDocumentParseStrategyTest {

    @Test
    void shouldMapOfficialOcrPageAndUrlsWithoutDownloadingImages() {
        AtomicInteger sourceOpenCount = new AtomicInteger();
        OcrGateway gateway = request -> {
            try (var sourceStream = request.openSourceStream()) {
                assertThat(sourceStream.readAllBytes()).containsExactly((byte) 1);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return new OcrDocumentResult("paddleocr-official", "PaddleOCR-VL-1.6", List.of(
                    new OcrPageResult(1, "# 图片识别结果",
                            Map.of("images/chart.png", "https://image.example/chart.png"),
                            Map.of("layout", "https://image.example/layout.jpg"))));
        };
        ParseExecutionContext context = new ParseExecutionContext(
                "01ARZ3NDEKTSV4RRFFQ69G5FAV", "01ARZ3NDEKTSV4RRFFQ69G5FAW",
                "01ARZ3NDEKTSV4RRFFQ69G5FAX", "01ARZ3NDEKTSV4RRFFQ69G5FAY",
                1, 1L, "01ARZ3NDEKTSV4RRFFQ69G5FAZ",
                "workspaces/source", "scan.png", "image/png", "abc",
                ParserType.BUILT_IN, Map.of(), () -> {
                    sourceOpenCount.incrementAndGet();
                    return new ByteArrayInputStream(new byte[]{1});
                });
        DocumentFileCapability capability = new DocumentFileCapability(
                "图片", "png", List.of("image/png"), "媒体元数据和原图锚点", List.of("OCR"), List.of());
        RawParseResult result = new ImageDocumentParseStrategy(gateway).parse(context, capability);

        assertThat(sourceOpenCount).hasValue(1);
        assertThat(result.blocks()).singleElement().satisfies(block -> {
            assertThat(block.type()).isEqualTo(ParsedBlock.PAGE_MARKDOWN);
            assertThat(block.anchor().page()).isEqualTo(1);
            assertThat(block.markdownImages()).containsEntry(
                    "images/chart.png", "https://image.example/chart.png");
            assertThat(block.outputImages()).containsEntry(
                    "layout", "https://image.example/layout.jpg");
        });
    }
}
