package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.OcrPlanMode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.OcrPlan;
import com.fons.cloud.ai.rag2okf.common.model.document.PdfPageProbeResult;
import com.fons.cloud.ai.rag2okf.common.model.document.PdfPageTextMetrics;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PDF 原生文本层的页级 OCR 探测器。
 *
 * <p>探测器只读取受控临时文件，不把源文件复制为内存字节数组，也不保存页面正文。
 * 低文本阈值与乱码阈值由配置提供，避免把业务经验固化在代码中。</p>
 *
 * @author hongqy
 */
@Component
public class PdfPageProbe {

    /** 默认 PDF 最大页数，与文档处理安全限制一致。 */
    public static final int DEFAULT_MAX_PAGES = 1000;

    /** 默认最小有效文本 Unicode 码点数。 */
    public static final int DEFAULT_MIN_TEXT_CODE_POINTS = 20;

    /** 默认最大非法字符比例。 */
    public static final double DEFAULT_MAX_INVALID_CHARACTER_RATIO = 0.05D;

    /** 默认可探测 PDF 源流字节上限。 */
    public static final long DEFAULT_MAX_SOURCE_BYTES = 100L * 1024 * 1024;

    private final int maxPages;
    private final int minTextCodePoints;
    private final double maxInvalidCharacterRatio;
    private final long maxSourceBytes;

    /**
     * 使用受控配置创建探测器。
     *
     * @param maxPages 最大可探测页数
     * @param minTextCodePoints 原生文本可用的最小有效 Unicode 码点数
     * @param maxInvalidCharacterRatio 可接受的最大非法字符比例
     */
    @Autowired
    public PdfPageProbe(
            @Value("${rag2okf.document.pdf.ocr-probe.max-pages:" + DEFAULT_MAX_PAGES + "}") int maxPages,
            @Value("${rag2okf.document.pdf.ocr-probe.min-text-code-points:"
                    + DEFAULT_MIN_TEXT_CODE_POINTS + "}") int minTextCodePoints,
            @Value("${rag2okf.document.pdf.ocr-probe.max-invalid-character-ratio:"
                    + DEFAULT_MAX_INVALID_CHARACTER_RATIO + "}") double maxInvalidCharacterRatio,
            @Value("${rag2okf.document.pdf.ocr-probe.max-source-bytes:"
                    + DEFAULT_MAX_SOURCE_BYTES + "}") long maxSourceBytes) {
        if (maxPages < 1 || minTextCodePoints < 1
                || maxInvalidCharacterRatio < 0 || maxInvalidCharacterRatio > 1 || maxSourceBytes < 1) {
            throw new IllegalArgumentException("invalid PDF OCR probe configuration");
        }
        this.maxPages = maxPages;
        this.minTextCodePoints = minTextCodePoints;
        this.maxInvalidCharacterRatio = maxInvalidCharacterRatio;
        this.maxSourceBytes = maxSourceBytes;
    }

    /**
     * 创建用于单元测试的页级探测器。
     *
     * @param maxPages 最大可探测页数
     * @param minTextCodePoints 原生文本可用的最小有效 Unicode 码点数
     * @param maxInvalidCharacterRatio 可接受的最大非法字符比例
     */
    public PdfPageProbe(int maxPages, int minTextCodePoints, double maxInvalidCharacterRatio) {
        this(maxPages, minTextCodePoints, maxInvalidCharacterRatio, DEFAULT_MAX_SOURCE_BYTES);
    }

    /**
     * 逐页探测 PDF 原生文本层，生成可解释的 OCR 计划。
     *
     * <p>PDFBox 只接受文件载体时，本方法在内部将受控流写入临时文件并在结束后删除；
     * 临时路径不会离开本方法。</p>
     *
     * @param sourceStream 从首字节开始的 PDF 输入流，调用方仍负责关闭
     * @return 页级指标和 OCR 计划
     * @throws DocumentProcessingException PDF 无法读取或超过页数限制时抛出安全错误
     */
    public PdfPageProbeResult probe(InputStream sourceStream) {
        if (sourceStream == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("rag2okf-pdf-probe-", ".pdf");
            copyToTemporaryFile(sourceStream, temporaryFile);
            try (PDDocument document = Loader.loadPDF(temporaryFile.toFile())) {
                return probeDocument(document);
            }
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR, exception);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    private PdfPageProbeResult probeDocument(PDDocument document) throws IOException {
        int pageCount = document.getNumberOfPages();
        if (pageCount < 1) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_FILE_SECURITY_REJECTED);
        }
        if (pageCount > maxPages) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_FILE_LIMIT_EXCEEDED);
        }
        List<PdfPageTextMetrics> pageMetrics = probePages(document);
        return new PdfPageProbeResult(pageCount, pageMetrics, plan(pageCount, pageMetrics));
    }

    private void copyToTemporaryFile(InputStream sourceStream, Path temporaryFile) throws IOException {
        byte[] buffer = new byte[8192];
        long totalBytes = 0;
        try (OutputStream outputStream = Files.newOutputStream(
                temporaryFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            int read;
            while ((read = sourceStream.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > maxSourceBytes) {
                    throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_FILE_LIMIT_EXCEEDED);
                }
                outputStream.write(buffer, 0, read);
            }
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // 临时文件清理由运行环境兜底；不得把路径写入业务错误或日志。
        }
    }

    private List<PdfPageTextMetrics> probePages(PDDocument document) throws IOException {
        PDFTextStripper textStripper = new PDFTextStripper();
        List<PdfPageTextMetrics> metrics = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
            textStripper.setStartPage(pageNumber);
            textStripper.setEndPage(pageNumber);
            metrics.add(evaluate(pageNumber, textStripper.getText(document)));
        }
        return List.copyOf(metrics);
    }

    private OcrPlan plan(int pageCount, List<PdfPageTextMetrics> metrics) {
        List<Integer> pageNumbers = metrics.stream()
                .filter(PdfPageTextMetrics::ocrRequired)
                .map(PdfPageTextMetrics::pageNumber)
                .toList();
        if (pageNumbers.isEmpty()) {
            return OcrPlan.none();
        }
        List<String> reasons = metrics.stream()
                .filter(PdfPageTextMetrics::ocrRequired)
                .map(metric -> "PAGE_" + metric.pageNumber() + ':' + metric.reason())
                .toList();
        OcrPlanMode mode = pageNumbers.size() == pageCount
                ? OcrPlanMode.REQUIRED : OcrPlanMode.PAGE_SELECTIVE;
        return new OcrPlan(mode, pageNumbers, reasons);
    }

    private PdfPageTextMetrics evaluate(int pageNumber, String text) {
        int[] codePoints = text == null ? new int[0] : text.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .toArray();
        int effectiveTextCodePoints = codePoints.length;
        if (effectiveTextCodePoints < minTextCodePoints) {
            return new PdfPageTextMetrics(pageNumber, effectiveTextCodePoints, 0D, true, "TEXT_BELOW_MINIMUM");
        }
        long invalidCharacterCount = Arrays.stream(codePoints)
                .filter(this::isInvalidCharacter)
                .count();
        double invalidCharacterRatio = (double) invalidCharacterCount / effectiveTextCodePoints;
        boolean ocrRequired = invalidCharacterRatio > maxInvalidCharacterRatio;
        return new PdfPageTextMetrics(
                pageNumber, effectiveTextCodePoints, invalidCharacterRatio, ocrRequired,
                ocrRequired ? "INVALID_CHARACTER_RATIO_EXCEEDED" : "TEXT_USABLE");
    }

    private boolean isInvalidCharacter(int codePoint) {
        return codePoint == 0xFFFD
                || (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint));
    }
}
