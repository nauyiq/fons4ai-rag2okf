package com.fons.cloud.ai.rag2okf.infrastructure.document.ocr;

import java.util.Map;
import java.util.Objects;

/**
 * OCR 的单页结果及官方图片地址。
 *
 * <p>图片地址仅透传给后续调用方，Gateway 不下载、不持久化或改写这些地址。</p>
 *
 * @param pageNumber 从 1 开始的源页码
 * @param markdown 页面 Markdown 文本
 * @param markdownImages Markdown 相对图片路径到官方 URL 的映射
 * @param outputImages 可视化图片名称到官方 URL 的映射
 * @author hongqy
 */
public record OcrPageResult(
        int pageNumber,
        String markdown,
        Map<String, String> markdownImages,
        Map<String, String> outputImages
) {

    /** OCR 页码的首个有效值。 */
    public static final int FIRST_PAGE_NUMBER = 1;

    /** 校验页码和页面内容，并创建不可变图片地址映射。 */
    public OcrPageResult {
        if (pageNumber < FIRST_PAGE_NUMBER) {
            throw new IllegalArgumentException("页码必须从 1 开始");
        }
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("页面 Markdown 不可为空");
        }
        markdownImages = immutableUrls(markdownImages, "Markdown 图片地址");
        outputImages = immutableUrls(outputImages, "可视化图片地址");
    }

    private static Map<String, String> immutableUrls(Map<String, String> urls, String name) {
        Objects.requireNonNull(urls, name + "不可为空");
        for (Map.Entry<String, String> entry : urls.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalArgumentException(name + "包含空路径或空 URL");
            }
        }
        return Map.copyOf(urls);
    }
}
