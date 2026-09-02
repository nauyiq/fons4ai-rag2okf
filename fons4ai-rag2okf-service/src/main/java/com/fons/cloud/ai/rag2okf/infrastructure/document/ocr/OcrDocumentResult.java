package com.fons.cloud.ai.rag2okf.infrastructure.document.ocr;

import java.util.List;
import java.util.Objects;

/**
 * OCR Gateway 返回的页级文档事实。
 *
 * @param engine 明确执行的 OCR 引擎标识
 * @param version 已受控的模型版本标识
 * @param pages 按源文件页序排列的结果
 * @author hongqy
 */
public record OcrDocumentResult(String engine, String version, List<OcrPageResult> pages) {

    /** 校验引擎、版本和页序结果。 */
    public OcrDocumentResult {
        if (engine == null || engine.isBlank()) {
            throw new IllegalArgumentException("OCR 引擎不可为空");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("OCR 版本不可为空");
        }
        Objects.requireNonNull(pages, "OCR 页面不可为空");
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("OCR 页面不可为空");
        }
        pages = List.copyOf(pages);
    }
}
