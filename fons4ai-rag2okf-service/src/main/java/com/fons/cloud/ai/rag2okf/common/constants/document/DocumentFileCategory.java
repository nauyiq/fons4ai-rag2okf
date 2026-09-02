package com.fons.cloud.ai.rag2okf.common.constants.document;

import lombok.Getter;

/**
 * Built-in 解析器支持的文件能力类别。
 *
 * <p>该值与 {@code DocumentFileCapability.category} 的持久化/任务快照值保持一致，
 * 用于能力清单与策略注册表之间的受控匹配。</p>
 *
 * @author hongqy
 */
@Getter
public enum DocumentFileCategory {

    /** 纯文本文件。 */
    TEXT("纯文本"),
    /** Markdown 文件。 */
    MARKDOWN("Markdown"),
    /** PDF 文件。 */
    PDF("PDF"),
    /** Word 文档。 */
    WORD("Word"),
    /** 图片文件。 */
    IMAGE("图片"),
    /** 音频文件。 */
    AUDIO("音频");

    /**
     * -- GETTER --
     *  获取写入文件能力快照的稳定类别值。
     *
     * @return 稳定类别值
     */
    private final String value;

    DocumentFileCategory(String value) {
        this.value = value;
    }

    /**
     * 判断文件能力是否属于当前类别。
     *
     * @param category 文件能力快照中的类别值
     * @return 是否匹配
     */
    public boolean matches(String category) {
        return value.equals(category);
    }
}
