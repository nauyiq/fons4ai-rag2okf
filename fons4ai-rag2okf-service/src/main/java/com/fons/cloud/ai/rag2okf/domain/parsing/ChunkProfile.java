package com.fons.cloud.ai.rag2okf.domain.parsing;

/**
 * 分块策略配置。
 *
 * @param strategy   分块策略：recursive 递归、markdown-header 标题层级
 * @param chunkSize  目标块大小（字符数）
 * @param overlap    块间重叠（字符数）
 * @author hongqy
 */
public record ChunkProfile(
        String strategy,
        int chunkSize,
        int overlap
) {
    /** 递归分块策略。 */
    public static final String RECURSIVE = "recursive";
    /** Markdown 标题分块策略。 */
    public static final String MARKDOWN_HEADER = "markdown-header";

    /** 默认递归分块：1000 字符、100 重叠。 */
    public static final ChunkProfile DEFAULT_RECURSIVE = new ChunkProfile(RECURSIVE, 1000, 100);

    /**
     * 构造 Markdown 标题分块策略。
     *
     * @param chunkSize 目标块大小
     * @param overlap   块间重叠
     * @return markdown-header 策略
     */
    public static ChunkProfile markdownHeader(int chunkSize, int overlap) {
        return new ChunkProfile(MARKDOWN_HEADER, chunkSize, overlap);
    }
}
