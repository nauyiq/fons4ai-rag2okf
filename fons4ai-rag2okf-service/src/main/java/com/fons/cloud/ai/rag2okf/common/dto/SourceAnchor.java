package com.fons.cloud.ai.rag2okf.common.dto;

/**
 * 来源定位信息。
 *
 * <p>不伪造页码：当 locatorType=NONE 时 page 和 blockIndex 均为 null。
 *
 * @param locatorType 定位类型：PAGE 精确页码、BLOCK 块序号、NONE 无来源定位
 * @param page        页码（仅 PAGE 时有值），从 1 开始
 * @param blockIndex  块序号（仅 BLOCK 时有值），从 0 开始
 * @author hongqy
 */
public record SourceAnchor(
        String locatorType,
        Integer page,
        Integer blockIndex
) {
    /** PAGE 定位类型。 */
    public static final String PAGE = "PAGE";
    /** BLOCK 定位类型。 */
    public static final String BLOCK = "BLOCK";
    /** NONE 定位类型，无来源信息。 */
    public static final String NONE = "NONE";

    /**
     * 构造无来源定位的 SourceAnchor。
     *
     * @return locatorType=NONE 的 SourceAnchor
     */
    public static SourceAnchor none() {
        return new SourceAnchor(NONE, null, null);
    }

    /**
     * 构造页码定位的 SourceAnchor。
     *
     * @param page 页码，从 1 开始
     * @return locatorType=PAGE 的 SourceAnchor
     */
    public static SourceAnchor page(int page) {
        return new SourceAnchor(PAGE, page, null);
    }

    /**
     * 构造块序号定位的 SourceAnchor。
     *
     * @param blockIndex 块序号，从 0 开始
     * @return locatorType=BLOCK 的 SourceAnchor
     */
    public static SourceAnchor block(int blockIndex) {
        return new SourceAnchor(BLOCK, null, blockIndex);
    }
}
