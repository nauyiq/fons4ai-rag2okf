package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support;

import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrDocumentResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.ocr.OcrPageResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * official OCR 页结果到文档原始块的白名单映射。
 *
 * @author hongqy
 */
final class OcrPageResultMapper {

    private OcrPageResultMapper() {
    }

    /**
     * 按 OCR Gateway 返回的源文件页序映射全部页面。
     *
     * @param result OCR 页级结果
     * @return 按源文件页序排列、均带 PAGE 锚点的原始块
     */
    static List<RawParseBlock> mapAll(OcrDocumentResult result) {
        return result.pages().stream()
                .map(page -> toRawBlock(result, page))
                .toList();
    }

    /**
     * 映射指定页码的 OCR 页结果。
     *
     * @param result OCR 页级结果
     * @param selectedPages 允许输出的页码集合
     * @return 符合页码白名单的原始块
     */
    static List<RawParseBlock> map(OcrDocumentResult result, Set<Integer> selectedPages) {
        return result.pages().stream()
                .filter(page -> selectedPages.contains(page.pageNumber()))
                .map(page -> toRawBlock(result, page))
                .collect(Collectors.toList());
    }

    private static RawParseBlock toRawBlock(OcrDocumentResult result, OcrPageResult page) {
        return new RawParseBlock(ParsedBlock.PAGE_MARKDOWN, page.markdown(), null, null, null,
                SourceAnchor.page(page.pageNumber()), Map.of(
                        "ocrEngine", result.engine(), "ocrVersion", result.version()),
                page.markdownImages(), page.outputImages());
    }
}
