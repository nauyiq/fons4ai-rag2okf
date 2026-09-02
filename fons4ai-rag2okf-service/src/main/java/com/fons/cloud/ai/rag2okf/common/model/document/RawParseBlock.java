package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析器提取的单个原始内容块。
 *
 * <p>稳定块标识和顺序由规范化器生成；本对象只表达解析器已经确认的内容与来源锚点。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@EqualsAndHashCode
public class RawParseBlock {

    /** 块类型，例如段落、标题、表格或图片。 */
    private String type;

    /** 块的文本内容；纯媒体块可为空。 */
    private String text;

    /** 标题层级；非标题块为空。 */
    private Integer level;

    /** 表格结构；仅表格块使用。 */
    private ParsedTable table;

    /** 媒体信息；仅图片或音频转写块使用。 */
    private ParsedMedia media;

    /** 从源文件中获得的位置锚点，不得伪造。 */
    private SourceAnchor anchor;

    /** 经白名单过滤的解析器补充属性。 */
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /** 页面 Markdown 中相对图片路径到官方图片 URL 的映射，仅 official OCR 页使用。 */
    private Map<String, String> markdownImages = new LinkedHashMap<>();

    /** 页面可视化图片名称到官方图片 URL 的映射，仅 official OCR 页使用。 */
    private Map<String, String> outputImages = new LinkedHashMap<>();

    /** 创建不含 OCR 页面图片地址的原始内容块。 */
    public RawParseBlock(String type, String text, Integer level, ParsedTable table, ParsedMedia media,
                         SourceAnchor anchor, Map<String, Object> attributes) {
        this(type, text, level, table, media, anchor, attributes, Map.of(), Map.of());
    }

    /** 创建可携带 official OCR 页面图片地址的原始内容块。 */
    public RawParseBlock(String type, String text, Integer level, ParsedTable table, ParsedMedia media,
                         SourceAnchor anchor, Map<String, Object> attributes,
                         Map<String, String> markdownImages, Map<String, String> outputImages) {
        this.type = type;
        this.text = text;
        this.level = level;
        this.table = table;
        this.media = media;
        this.anchor = anchor;
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        this.markdownImages = markdownImages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(markdownImages);
        this.outputImages = outputImages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(outputImages);
    }

    /**
     * 创建文本变化后的副本。
     *
     * @param value 新文本内容
     * @return 保留其余解析事实的新原始块
     */
    public RawParseBlock withText(String value) {
        return new RawParseBlock(type, value, level, table, media, anchor,
                new LinkedHashMap<>(attributes), new LinkedHashMap<>(markdownImages),
                new LinkedHashMap<>(outputImages));
    }
}
