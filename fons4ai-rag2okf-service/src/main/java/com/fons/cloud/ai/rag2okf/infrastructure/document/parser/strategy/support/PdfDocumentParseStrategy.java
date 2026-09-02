package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentFileCategory;
import com.fons.cloud.ai.rag2okf.common.constants.document.OcrPlanMode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentSourceStreamProvider;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.PdfPageProbeResult;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.document.BuiltInBaseExtractor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrDocumentRequest;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrDocumentResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrGateway;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.PdfPageProbe;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParseStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * PDF 文件类型策略中的页级文本层探测协作。
 *
 * <p>该策略拥有 PDF 特有的 probe；识别器和其他文件类型均不依赖 PDFBox 或 OCR 计划。
 * 后续原生提取和 OCR 必须再次调用同一读取器取得独立流。</p>
 *
 * @author hongqy
 */
@Component
public class PdfDocumentParseStrategy implements DocumentParseStrategy {

    private final PdfPageProbe pdfPageProbe;
    private final BuiltInBaseExtractor baseExtractor;
    private final ObjectProvider<OcrGateway> ocrGatewayProvider;

    /**
     * 创建 PDF 策略。
     *
     * @param pdfPageProbe PDF 页级文本层探测器
     */
    @Autowired
    public PdfDocumentParseStrategy(
            PdfPageProbe pdfPageProbe,
            BuiltInBaseExtractor baseExtractor,
            ObjectProvider<OcrGateway> ocrGatewayProvider) {
        this.pdfPageProbe = pdfPageProbe;
        this.baseExtractor = baseExtractor;
        this.ocrGatewayProvider = ocrGatewayProvider;
    }

    /**
     * 仅用于页级 probe 的测试构造器。
     *
     * @param pdfPageProbe PDF 页级文本层探测器
     */
    public PdfDocumentParseStrategy(PdfPageProbe pdfPageProbe) {
        this(pdfPageProbe, null, null);
    }

    @Override
    public boolean supports(DocumentFileCapability capability) {
        return DocumentFileCategory.PDF.matches(capability.category());
    }

    @Override
    public RawParseResult parse(ParseExecutionContext context, DocumentFileCapability capability) {
        PdfPageProbeResult probeResult = probe(context::openSourceStream);
        RawParseResult nativeResult = baseExtractor.extractText(
                context, probeResult.ocrPlan().mode() == OcrPlanMode.NONE);
        if (probeResult.ocrPlan().mode() == OcrPlanMode.NONE) {
            return copyWithMetadata(nativeResult, probeResult.pageCount(), List.of());
        }
        OcrDocumentResult ocrResult = requireOcrGateway().parse(
                new OcrDocumentRequest(context.filename(), context::openSourceStream));
        List<RawParseBlock> blocks = new ArrayList<>(nativeResult.blocks());
        blocks.addAll(OcrPageResultMapper.map(ocrResult, new LinkedHashSet<>(probeResult.ocrPlan().pageNumbers())));
        if (blocks.isEmpty()) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR);
        }
        return copyWithMetadata(nativeResult, probeResult.pageCount(), List.copyOf(blocks));
    }

    /**
     * 使用独立源流执行 PDF 页级 probe。
     *
     * @param sourceStreamProvider 可重复打开的受控源流读取器
     * @return 页级文本指标与 OCR 计划
     */
    public PdfPageProbeResult probe(DocumentSourceStreamProvider sourceStreamProvider) {
        Objects.requireNonNull(sourceStreamProvider, "sourceStreamProvider must not be null");
        try (InputStream sourceStream = sourceStreamProvider.openStream()) {
            return pdfPageProbe.probe(sourceStream);
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR, exception);
        }
    }

    /**
     * 获取当前运行时装配的 OCR Gateway。
     *
     * @return official OCR Gateway
     * @throws DocumentProcessingException 未装配 OCR 能力时抛出
     */
    private OcrGateway requireOcrGateway() {
        OcrGateway gateway = ocrGatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.MODEL_CAPABILITY_MISSING);
        }
        return gateway;
    }

    /**
     * 将 PDF probe 得到的页数写回原生提取结果，并替换为最终采用的有序块。
     *
     * <p>来源锚点与警告由实际产出块的策略负责提供；本方法不通过猜测空锚点补写 warning，
     * 避免掩盖不合法的原始块。</p>
     *
     * @param source 原生提取结果
     * @param pageCount PDF probe 确认的总页数
     * @param blocks OCR 合并后的块；为空时保留原生块
     * @return 带 PDF 页数和最终块的原始结果
     */
    private RawParseResult copyWithMetadata(
            RawParseResult source, int pageCount, List<RawParseBlock> blocks) {
        List<RawParseBlock> resolvedBlocks = blocks.isEmpty() ? source.blocks() : blocks;
        return new RawParseResult(source.title(), source.language(), pageCount, source.durationMs(),
                List.copyOf(resolvedBlocks), List.copyOf(source.warnings()), List.copyOf(source.trace()));
    }
}
