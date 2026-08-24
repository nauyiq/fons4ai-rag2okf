package com.fons.cloud.ai.rag2okf.common.model.document;

import java.util.Objects;

/**
 * ParsedDocument v1 来源锚点。
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
 * @param locatorType    定位类型，必填
 * @param page           页码，从 1 开始；PAGE/PARAGRAPH/REGION 必填
 * @param paragraphIndex 段落序号，从 0 开始；PARAGRAPH 必填
 * @param region         归一化区域坐标；REGION 必填
 * @param timeRange      时间范围；TIME_RANGE 必填
 * @author hongqy
 */
public record SourceAnchor(
    String locatorType,
    Integer page,
    Integer paragraphIndex,
    Region region,
    TimeRange timeRange
) {

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

    /** 紧凑构造器，校验定位类型非空。字段约束由 {@link ParsedDocumentValidator} 统一校验。 */
    public SourceAnchor {
        Objects.requireNonNull(locatorType, "locatorType must not be null");
    }

    /** 创建 PAGE 锚点。 */
    public static SourceAnchor page(int page) {
        return new SourceAnchor(PAGE, page, null, null, null);
    }

    /** 创建 PARAGRAPH 锚点。 */
    public static SourceAnchor paragraph(int page, int paragraphIndex) {
        return new SourceAnchor(PARAGRAPH, page, paragraphIndex, null, null);
    }

    /** 创建 REGION 锚点。 */
    public static SourceAnchor region(int page, Region region) {
        return new SourceAnchor(REGION, page, null, region, null);
    }

    /** 创建 TIME_RANGE 锚点。 */
    public static SourceAnchor timeRange(TimeRange timeRange) {
        return new SourceAnchor(TIME_RANGE, null, null, null, timeRange);
    }

    /** 创建 NONE 锚点。 */
    public static SourceAnchor none() {
        return new SourceAnchor(NONE, null, null, null, null);
    }

    /**
     * 归一化区域坐标。
     *
     * <p>坐标值范围 [0, 1]，原点为页面左上角。{@code width} 和 {@code height} 为正数，
     * 且 {@code x + width <= 1}、{@code y + height <= 1}。</p>
     *
     * @param x      左上角横坐标，[0, 1]
     * @param y      左上角纵坐标，[0, 1]
     * @param width  宽度，(0, 1]
     * @param height 高度，(0, 1]
     */
    public record Region(double x, double y, double width, double height) {
        public Region {
            Objects.requireNonNull(x, "x must not be null");
            Objects.requireNonNull(y, "y must not be null");
            Objects.requireNonNull(width, "width must not be null");
            Objects.requireNonNull(height, "height must not be null");
        }
    }

    /**
     * 音视频时间范围。
     *
     * @param startMs 起始毫秒，>= 0
     * @param endMs   结束毫秒，> startMs
     */
    public record TimeRange(long startMs, long endMs) {
        public TimeRange {
            Objects.requireNonNull(startMs, "startMs must not be null");
            Objects.requireNonNull(endMs, "endMs must not be null");
        }
    }
}
