package com.fons.cloud.ai.rag2okf.infrastructure.document.ocr;

import com.fons.cloud.ai.capability.constants.AiCapabilityResultCode;
import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentPageResult;
import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentParser;
import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentRequest;
import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentResult;
import com.fons.cloud.ai.capability.ocr.PaddleOcrProvider;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** `paddleocr-official` 页级 Gateway 的契约测试。 */
class PaddleOcrOfficialGatewayTest {

    @Test
    void shouldMapOrderedPagesAndImageUrls() {
        PaddleOcrOfficialGateway gateway = new PaddleOcrOfficialGateway(new FixedParser(successfulResult()));
        AtomicInteger sourceOpenCount = new AtomicInteger();

        OcrDocumentResult result = gateway.parse(new OcrDocumentRequest("report.pdf", () -> {
            sourceOpenCount.incrementAndGet();
            return new ByteArrayInputStream(new byte[]{1, 2});
        }));

        assertEquals("paddleocr-official", result.engine());
        assertEquals("PaddleOCR-VL-1.6", result.version());
        assertEquals(2, result.pages().size());
        assertEquals(1, result.pages().getFirst().pageNumber());
        assertEquals("# first", result.pages().getFirst().markdown());
        assertEquals(Map.of("images/chart.png", "https://image.example/chart.png"),
                result.pages().getFirst().markdownImages());
        assertEquals(2, result.pages().get(1).pageNumber());
        assertEquals(Map.of("layout", "https://image.example/layout.jpg"),
                result.pages().get(1).outputImages());
        assertEquals(1, sourceOpenCount.get());
    }

    @Test
    void shouldMapCapabilityFailureToSafeDocumentException() {
        PaddleOcrOfficialGateway gateway = new PaddleOcrOfficialGateway(
                new FailingParser(BusinessRuntimeException.of(AiCapabilityResultCode.PADDLEOCR_DOCUMENT_PARSE_TIMEOUT)));

        DocumentProcessingException exception = assertThrows(DocumentProcessingException.class,
                () -> gateway.parse(new OcrDocumentRequest(
                        "secret.pdf", () -> new ByteArrayInputStream(new byte[]{1}))));

        assertEquals(Rag2OkfResultCode.MODEL_CAPABILITY_CALL_FAILED.getCode(), exception.getCode());
        assertFalse(exception.getMessage().contains("secret.pdf"));
    }

    @Test
    void shouldRejectNonOfficialParserWithoutFallback() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaddleOcrOfficialGateway(new ProviderOnlyParser(PaddleOcrProvider.PADDLEOCR_LOCAL)));
    }

    private PaddleOcrDocumentResult successfulResult() {
        return new PaddleOcrDocumentResult("# first\n\n# second", List.of(
                new PaddleOcrDocumentPageResult("# first",
                        Map.of("images/chart.png", "https://image.example/chart.png"), Map.of()),
                new PaddleOcrDocumentPageResult("# second", Map.of(),
                        Map.of("layout", "https://image.example/layout.jpg"))
        ), PaddleOcrProvider.PADDLEOCR_OFFICIAL, Duration.ofMillis(10));
    }

    private static final class FixedParser implements PaddleOcrDocumentParser {

        private final PaddleOcrDocumentResult result;

        private FixedParser(PaddleOcrDocumentResult result) {
            this.result = result;
        }

        @Override
        public PaddleOcrProvider provider() {
            return PaddleOcrProvider.PADDLEOCR_OFFICIAL;
        }

        @Override
        public PaddleOcrDocumentResult parse(PaddleOcrDocumentRequest request) {
            return result;
        }
    }

    private static final class FailingParser implements PaddleOcrDocumentParser {

        private final BusinessRuntimeException exception;

        private FailingParser(BusinessRuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public PaddleOcrProvider provider() {
            return PaddleOcrProvider.PADDLEOCR_OFFICIAL;
        }

        @Override
        public PaddleOcrDocumentResult parse(PaddleOcrDocumentRequest request) {
            throw exception;
        }
    }

    private static final class ProviderOnlyParser implements PaddleOcrDocumentParser {

        private final PaddleOcrProvider provider;

        private ProviderOnlyParser(PaddleOcrProvider provider) {
            this.provider = provider;
        }

        @Override
        public PaddleOcrProvider provider() {
            return provider;
        }

        @Override
        public PaddleOcrDocumentResult parse(PaddleOcrDocumentRequest request) {
            throw new UnsupportedOperationException("不应调用非官方 Provider");
        }
    }
}
