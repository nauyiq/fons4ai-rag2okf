package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * ParsedDocument 来源锚点。
 *
 * <p>联合结构，对应 {@code PAGE/PARAGRAPH/REGION/TIME_RANGE/NONE} 五种定位类型。
 * 不同类型对应不同字段约束：</p>
 * <ul>
 *   <li>{@link #PAGE} — {@code page} 必填，从 1 开始；其余字段为 null</li>
 *   <li>{@link #PARAGRAPH} — {@code page} 必填，{@code paragraphIndex} 必填，从 0 开始</li>
 *   <li>{@link #REGION} — {@code page} 必填，{@code region} 必填且坐标归一化到 [0, 1]</li>
 *   <li>{@link #TIME_RANGE} — {@code timeRange} 必填，{@code startMs} 和 {@code endMs} 从 0 开始且 {@code endMs > startMs}</li>
 *   <li>{@link #NONE} — 全部字段为 null；解析器不得默认使用 NONE，仅确定性提取确实无法定位时使用，且必须附 {@code SOURCE_ANCHOR_UNAVAILABLE} warning</li>
 * </ul>
 *
 * <p>REGION 坐标归一化到 [0, 1]，避免不同 DPI/页面尺寸下的歧义。
 * 锚点不得伪造页码或时间。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SourceAnchor {

    /** 定位类型，取值为 PAGE、PARAGRAPH、REGION、TIME_RANGE 或 NONE。 */
    private String locatorType;
    /** 页码，从 1 开始；PAGE、PARAGRAPH、REGION 类型必填。 */
    private Integer page;
    /** 页内段落序号，从 0 开始；PARAGRAPH 类型必填。 */
    private Integer paragraphIndex;
    /** 归一化区域坐标；REGION 类型必填。 */
    private SourceRegion region;
    /** 媒体时间范围；TIME_RANGE 类型必填。 */
    private SourceTimeRange timeRange;

    /** PAGE 定位类型：精确页码。 */
    public static final String PAGE = "PAGE";
    /** PARAGRAPH 定位类型：页码 + 段落序号。 */
    public static final String PARAGRAPH = "PARAGRAPH";
    /** REGION 定位类型：页码 + 归一化区域坐标。 */
    public static final String REGION = "REGION";
    /** TIME_RANGE 定位类型：音视频时间范围。 */
    public static final String TIME_RANGE = "TIME_RANGE";
    /** NONE 定位类型：无来源定位。 */
    public static final String NONE = "NONE";
    /** NONE 锚点必须携带的来源不可定位警告。 */
    public static final String SOURCE_ANCHOR_UNAVAILABLE_WARNING = "SOURCE_ANCHOR_UNAVAILABLE";

    /** 创建 PAGE 锚点。 */
    public static SourceAnchor page(int page) {
        return new SourceAnchor(PAGE, page, null, null, null);
    }

    /** 创建 PARAGRAPH 锚点。 */
    public static SourceAnchor paragraph(int page, int paragraphIndex) {
        return new SourceAnchor(PARAGRAPH, page, paragraphIndex, null, null);
    }

    /** 创建 REGION 锚点。 */
    public static SourceAnchor region(int page, SourceRegion region) {
        return new SourceAnchor(REGION, page, null, region, null);
    }

    /** 创建 TIME_RANGE 锚点。 */
    public static SourceAnchor timeRange(SourceTimeRange timeRange) {
        return new SourceAnchor(TIME_RANGE, null, null, null, timeRange);
    }

    /** 创建 NONE 锚点。 */
    public static SourceAnchor none() {
        return new SourceAnchor(NONE, null, null, null, null);
    }

}
