package com.fons.cloud.ai.rag2okf.common.model.document;

import java.util.List;
import java.util.Objects;

/**
 * PDF 页级探测结果。
 *
 * @param pageCount PDF 总页数
 * @param pageMetrics 每页安全化质量指标
 * @param ocrPlan 根据页级指标汇总出的 OCR 计划
 * @author hongqy
 */
public record PdfPageProbeResult(int pageCount, List<PdfPageTextMetrics> pageMetrics, OcrPlan ocrPlan) {

    public PdfPageProbeResult {
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be positive");
        }
        pageMetrics = List.copyOf(pageMetrics);
        Objects.requireNonNull(ocrPlan, "ocrPlan must not be null");
    }
}
