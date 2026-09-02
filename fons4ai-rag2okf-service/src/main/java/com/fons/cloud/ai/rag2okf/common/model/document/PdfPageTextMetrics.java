package com.fons.cloud.ai.rag2okf.common.model.document;

/**
 * PDF 页级文本层质量指标。
 *
 * <p>指标只用于决定 OCR 范围，不保存页面正文。有效字符数按去除空白后的 Unicode 码点计算，
 * 非法字符比率统计替换字符和不应出现在文本层中的控制字符。</p>
 *
 * @param pageNumber 从 1 开始的页序
 * @param effectiveTextCodePoints 有效文本 Unicode 码点数
 * @param invalidCharacterRatio 非法字符占有效字符数的比例
 * @param ocrRequired 当前页是否需要 OCR
 * @param reason 安全化判定依据
 * @author hongqy
 */
public record PdfPageTextMetrics(
        int pageNumber,
        int effectiveTextCodePoints,
        double invalidCharacterRatio,
        boolean ocrRequired,
        String reason) {
}
