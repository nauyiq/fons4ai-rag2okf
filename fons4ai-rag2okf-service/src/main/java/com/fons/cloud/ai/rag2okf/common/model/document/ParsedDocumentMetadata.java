package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 从源内容中提取的文档级元数据。
 * @author hongqy
 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParsedDocumentMetadata {
    /**
     * 文档标题。
     */
    private String title;
    /**
     * 内容语言。
     */
    private String language;
    /**
     * 页数。
     */
    private Integer pageCount;
    /**
     * 媒体时长，单位毫秒。
     */
    private Long durationMs;
}
