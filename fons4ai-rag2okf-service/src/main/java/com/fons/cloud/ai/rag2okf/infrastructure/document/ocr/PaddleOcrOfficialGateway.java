package com.fons.cloud.ai.rag2okf.infrastructure.document.ocr;

import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentPageResult;
import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentParser;
import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentResult;
import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentStreamRequest;
import com.fons.cloud.ai.capability.ocr.PaddleOcrProvider;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.common.base.exception.BusinessRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * `paddleocr-official` 的页级 OCR Gateway 适配器。
 *
 * <p>只接收 official 解析器，不提供 local 或其他 Parser 的回退路径。</p>
 *
 * @author hongqy
 */
public class PaddleOcrOfficialGateway implements OcrGateway {

    private static final String ENGINE = "paddleocr-official";
    private static final String VERSION = "PaddleOCR-VL-1.6";

    private final PaddleOcrDocumentParser parser;

    /**
     * @param parser 已明确绑定 `paddleocr-official` 的 Fons4AI 解析器
     */
    public PaddleOcrOfficialGateway(PaddleOcrDocumentParser parser) {
        this.parser = Objects.requireNonNull(parser, "PaddleOCR 解析器不可为空");
        if (parser.provider() != PaddleOcrProvider.PADDLEOCR_OFFICIAL) {
            throw new IllegalArgumentException("PaddleOcrOfficialGateway 仅支持 paddleocr-official");
        }
    }

    @Override
    public OcrDocumentResult parse(OcrDocumentRequest request) {
        Objects.requireNonNull(request, "OCR 请求不可为空");
        try {
            PaddleOcrDocumentResult result = parser.parse(
                    new PaddleOcrDocumentStreamRequest(request.fileName(), request::openSourceStream));
            return new OcrDocumentResult(ENGINE, VERSION, mapPages(result.pages()));
        } catch (BusinessRuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.MODEL_CAPABILITY_CALL_FAILED, exception);
        } catch (IllegalArgumentException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID, exception);
        }
    }

    /**
     * Fons4AI 页列表按原始文档顺序返回，Rag2OKF 将零基索引转换为首个有效业务页码。
     */
    private List<OcrPageResult> mapPages(List<PaddleOcrDocumentPageResult> pages) {
        List<OcrPageResult> results = new ArrayList<>(pages.size());
        for (int index = 0; index < pages.size(); index++) {
            PaddleOcrDocumentPageResult page = pages.get(index);
            results.add(new OcrPageResult(index + OcrPageResult.FIRST_PAGE_NUMBER,
                    page.markdown(), page.markdownImages(), page.outputImages()));
        }
        return results;
    }
}
