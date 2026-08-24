package com.fons.cloud.ai.rag2okf.common.model.document;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ParsedDocument v1 块结构。
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
 * @param blockId       块 ID，result 版本内唯一
 * @param ordinal       序号，从 0 连续递增
 * @param type          块类型
 * @param parentBlockId 父块 ID，可空
 * @param text          文本内容；文本型块必填
 * @param level         标题层级，仅 HEADING 使用，1～6
 * @param table          表格结构，仅 TABLE 使用
 * @param media          媒体元数据，仅 IMAGE/AUDIO_TRANSCRIPT 使用
 * @param anchor         来源锚点
 * @param attributes     Schema 白名单标量属性
 * @author hongqy
 */
public record ParsedBlock(
    String blockId,
    int ordinal,
    String type,
    String parentBlockId,
    String text,
    Integer level,
    Table table,
    Media media,
    SourceAnchor anchor,
    Map<String, Object> attributes
) {

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

    /** 文本型块类型集合。 */
    public static final List<String> TEXT_TYPES = List.of(
        TITLE, HEADING, PARAGRAPH, LIST_ITEM, CODE, AUDIO_TRANSCRIPT
    );

    /** 紧凑构造器，防御性拷贝并拒绝空值。字段约束由 {@link ParsedDocumentValidator} 统一校验。 */
    public ParsedBlock {
        Objects.requireNonNull(blockId, "blockId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(anchor, "anchor must not be null");
        table = table == null ? null : table;
        media = media == null ? null : media;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 表格结构。
     *
     * @param rows    行数，> 0
     * @param columns 列数，> 0
     * @param cells   单元格列表，按行优先顺序
     */
    public record Table(int rows, int columns, List<TableCell> cells) {
        public Table {
            Objects.requireNonNull(cells, "cells must not be null");
            cells = List.copyOf(cells);
        }
    }

    /**
     * 表格单元格。
     *
     * @param row     行序号，从 0 开始
     * @param column  列序号，从 0 开始
     * @param text    单元格文本
     * @param rowSpan 跨行数，默认 1
     * @param colSpan 跨列数，默认 1
     */
    public record TableCell(int row, int column, String text, int rowSpan, int colSpan) {
        public TableCell {
            Objects.requireNonNull(text, "cell text must not be null");
        }
    }

    /**
     * 媒体元数据。
     *
     * @param mediaType   媒体类型，如 {@code image/png}、{@code audio/wav}
     * @param objectKey   可选 MinIO 引用；不保存公开 URL
     * @param alt         替代文本
     * @param description 描述
     */
    public record Media(String mediaType, String objectKey, String alt, String description) {
        public Media {
            Objects.requireNonNull(mediaType, "mediaType must not be null");
        }
    }
}
