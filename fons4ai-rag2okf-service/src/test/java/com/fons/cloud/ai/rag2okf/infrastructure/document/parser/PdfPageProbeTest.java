package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.OcrPlanMode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentSourceStreamProvider;
import com.fons.cloud.ai.rag2okf.common.model.document.PdfPageProbeResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support.PdfDocumentParseStrategy;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PDF 原生文本层的页级 OCR 计划测试。 */
class PdfPageProbeTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldSelectOnlyPagesWithInsufficientNativeText() throws IOException {
        Path sourceFile = writePdf(
                "This page contains enough native text to remain on the deterministic extraction path.",
                "scan");

        PdfPageProbeResult result = probe(sourceFile);

        assertThat(result.ocrPlan().mode()).isEqualTo(OcrPlanMode.PAGE_SELECTIVE);
        assertThat(result.ocrPlan().pageNumbers()).containsExactly(2);
        assertThat(result.pageMetrics()).extracting(metric -> metric.reason())
                .containsExactly("TEXT_USABLE", "TEXT_BELOW_MINIMUM");
    }

    @Test
    void shouldRequireOcrWhenEveryPageHasNoUsableText() throws IOException {
        PdfPageProbeResult result = probe(writePdf(null, ""));

        assertThat(result.ocrPlan().mode()).isEqualTo(OcrPlanMode.REQUIRED);
        assertThat(result.ocrPlan().pageNumbers()).containsExactly(1, 2);
    }

    @Test
    void shouldRejectPdfExceedingConfiguredPageLimit() throws IOException {
        Path sourceFile = writePdf("first", "second");

        assertThatThrownBy(() -> {
            try (InputStream sourceStream = Files.newInputStream(sourceFile)) {
                new PdfPageProbe(1, 20, 0.05D).probe(sourceStream);
            }
        })
                .isInstanceOf(DocumentProcessingException.class)
                .extracting("code")
                .isEqualTo(Rag2OkfResultCode.DOCUMENT_FILE_LIMIT_EXCEEDED.getCode());
    }

    @Test
    void shouldKeepSourceReadableWhenPdfStrategyCompletesProbe() throws IOException {
        byte[] sourceBytes = Files.readAllBytes(writePdf("native text that is long enough to avoid OCR"));
        AtomicInteger openCount = new AtomicInteger();
        PdfDocumentParseStrategy strategy = new PdfDocumentParseStrategy(new PdfPageProbe(1000, 20, 0.05D));

        DocumentSourceStreamProvider sourceStreamProvider = () -> {
            openCount.incrementAndGet();
            return new ByteArrayInputStream(sourceBytes);
        };

        PdfPageProbeResult result = strategy.probe(sourceStreamProvider);

        assertThat(result.ocrPlan().mode()).isEqualTo(OcrPlanMode.NONE);
        try (InputStream nextStream = sourceStreamProvider.openStream()) {
            assertThat(nextStream.readAllBytes()).isEqualTo(sourceBytes);
        }
        assertThat(openCount).hasValue(2);
    }

    @Test
    void shouldRejectDamagedInputStreamWithoutExposingTemporaryPath() {
        assertThatThrownBy(() -> new PdfPageProbe(1000, 20, 0.05D)
                .probe(new ByteArrayInputStream("not-a-pdf".getBytes())))
                .isInstanceOf(DocumentProcessingException.class)
                .extracting("code")
                .isEqualTo(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR.getCode());
    }

    private PdfPageProbeResult probe(Path sourceFile) throws IOException {
        try (InputStream sourceStream = Files.newInputStream(sourceFile)) {
            return new PdfPageProbe(1000, 20, 0.05D).probe(sourceStream);
        }
    }

    private Path writePdf(String... pageTexts) throws IOException {
        Path sourceFile = tempDirectory.resolve("source-" + System.nanoTime() + ".pdf");
        try (PDDocument document = new PDDocument()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (pageText == null || pageText.isBlank()) {
                    continue;
                }
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(pageText);
                    content.endText();
                }
            }
            document.save(sourceFile.toFile());
        }
        return sourceFile;
    }
}
