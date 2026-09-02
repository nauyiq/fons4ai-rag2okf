package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentFileCategory;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTraceStep;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrDocumentRequest;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrDocumentResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrGateway;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrPageResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParseStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 图片文件的 official OCR 解析策略。
 *
 * @author hongqy
 */
@Component
public class ImageDocumentParseStrategy implements DocumentParseStrategy {

    private final OcrGateway ocrGateway;

    @Autowired
    public ImageDocumentParseStrategy(ObjectProvider<OcrGateway> ocrGatewayProvider) {
        this(ocrGatewayProvider.getIfAvailable());
    }

    /**
     * 创建图片 OCR 策略。
     *
     * @param ocrGateway 已明确选择的 official OCR Gateway；未装配时为 null
     */
    public ImageDocumentParseStrategy(OcrGateway ocrGateway) {
        this.ocrGateway = ocrGateway;
    }

    @Override
    public boolean supports(DocumentFileCapability capability) {
        return DocumentFileCategory.IMAGE.matches(capability.category());
    }

    @Override
    public RawParseResult parse(ParseExecutionContext context, DocumentFileCapability capability) {
        if (ocrGateway == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.MODEL_CAPABILITY_MISSING);
        }
        OcrDocumentResult ocrResult = ocrGateway.parse(
                new OcrDocumentRequest(context.filename(), context::openSourceStream));
        List<RawParseBlock> blocks = OcrPageResultMapper.map(ocrResult,
                Set.of(OcrPageResult.FIRST_PAGE_NUMBER));
        if (blocks.isEmpty()) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR);
        }
        return new RawParseResult(null, null, blocks.size(), null, blocks, List.of(), List.of(
                new ParseTraceStep("OFFICIAL_OCR", "OCR", null, ParseTraceStep.STATUS_SUCCESS,
                        ParseTraceStep.UNKNOWN_DURATION_MILLIS, null)));
    }
}
