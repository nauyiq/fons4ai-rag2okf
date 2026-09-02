package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 规范化文档块结构。
 *
 * <p>扁平有序结构，避免递归深度和局部更新歧义。所有块共享以下约束：</p>
 * <ul>
 *   <li>{@code blockId} — 当前 result 版本内唯一、不可变；由规范化顺序和内容 hash 派生</li>
 *   <li>{@code ordinal} — 从 0 连续递增</li>
 *   <li>{@code type} — {@link #TITLE}、{@link #HEADING}、{@link #PARAGRAPH}、{@link #LIST_ITEM}、
 *       {@link #TABLE}、{@link #IMAGE}、{@link #AUDIO_TRANSCRIPT}、{@link #CODE}、{@link #PAGE_BREAK}</li>
 *   <li>{@code parentBlockId} — 可空；只能指向同一文档中更早且允许成为父级的 block</li>
 *   <li>{@code text} — 文本型块必填；纯媒体/PageBreak 可空</li>
 *   <li>{@code level} — 仅 HEADING 使用，范围 1～6</li>
 *   <li>{@code table} — 表格结构；仅 TABLE 类型使用</li>
 *   <li>{@code media} — 媒体元数据；仅 IMAGE/AUDIO_TRANSCRIPT 使用</li>
 *   <li>{@code anchor} — 来源锚点，不得伪造</li>
 *   <li>{@code attributes} — 仅允许 Schema 白名单标量，禁止任意对象透传模型/厂商响应</li>
 * </ul>
 *
 * @author hongqy
 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@EqualsAndHashCode
public class ParsedBlock {

    /** 当前解析结果内唯一的稳定块标识。 */
    private String blockId;

    /** 块在文档中的连续序号，从 0 开始。 */
    private int ordinal;

    /** 块类型。 */
    private String type;

    /** 父块标识；没有父级时为空。 */
    private String parentBlockId;

    /** 文本内容；纯媒体或分页符块可为空。 */
    private String text;

    /** 标题层级；仅 HEADING 类型使用。 */
    private Integer level;

    /** 表格结构；仅 TABLE 类型使用。 */
    private ParsedTable table;

    /** 媒体信息；仅 IMAGE 或 AUDIO_TRANSCRIPT 类型使用。 */
    private ParsedMedia media;

    /** 从源文件提取的位置锚点。 */
    private SourceAnchor anchor;

    /** 经过白名单过滤的标量属性。 */
    private Map<String, Object> attributes = Map.of();

    /** 页面 Markdown 中相对图片路径到官方图片 URL 的映射，仅 official OCR 页使用。 */
    private Map<String, String> markdownImages = Map.of();

    /** 页面可视化图片名称到官方图片 URL 的映射，仅 official OCR 页使用。 */
    private Map<String, String> outputImages = Map.of();

    /** 标题块（文档标题，通常一个）。 */
    public static final String TITLE = "TITLE";
    /** 标题块（章节标题，携带 level）。 */
    public static final String HEADING = "HEADING";
    /** 段落块。 */
    public static final String PARAGRAPH = "PARAGRAPH";
    /** 列表项块。 */
    public static final String LIST_ITEM = "LIST_ITEM";
    /** 表格块。 */
    public static final String TABLE = "TABLE";
    /** 图片块。 */
    public static final String IMAGE = "IMAGE";
    /** 音频转写块。 */
    public static final String AUDIO_TRANSCRIPT = "AUDIO_TRANSCRIPT";
    /** 代码块。 */
    public static final String CODE = "CODE";
    /** 分页符块。 */
    public static final String PAGE_BREAK = "PAGE_BREAK";
    /** official OCR 返回的整页 Markdown，不推断其内部版面块类型。 */
    public static final String PAGE_MARKDOWN = "PAGE_MARKDOWN";

    /** 文本型块类型集合。 */
    public static final List<String> TEXT_TYPES = List.of(
        TITLE, HEADING, PARAGRAPH, LIST_ITEM, CODE, AUDIO_TRANSCRIPT
    );

    /** 创建不含 OCR 图片地址的规范化块。 */
    public ParsedBlock(String blockId, int ordinal, String type, String parentBlockId, String text,
                       Integer level, ParsedTable table, ParsedMedia media, SourceAnchor anchor,
                       Map<String, Object> attributes) {
        this(blockId, ordinal, type, parentBlockId, text, level, table, media, anchor,
                attributes, Map.of(), Map.of());
    }

    /** 创建可携带 official OCR 页面图片地址的规范化块。 */
    public ParsedBlock(String blockId, int ordinal, String type, String parentBlockId, String text,
                       Integer level, ParsedTable table, ParsedMedia media, SourceAnchor anchor,
                       Map<String, Object> attributes, Map<String, String> markdownImages,
                       Map<String, String> outputImages) {
        this.blockId = blockId;
        this.ordinal = ordinal;
        this.type = type;
        this.parentBlockId = parentBlockId;
        this.text = text;
        this.level = level;
        this.table = table;
        this.media = media;
        this.anchor = anchor;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        this.markdownImages = markdownImages == null ? Map.of() : Map.copyOf(markdownImages);
        this.outputImages = outputImages == null ? Map.of() : Map.copyOf(outputImages);
    }

}
