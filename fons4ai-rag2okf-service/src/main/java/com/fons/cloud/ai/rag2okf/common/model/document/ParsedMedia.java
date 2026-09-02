package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 规范化块引用的媒体信息。 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParsedMedia {

    /** 媒体 MIME 类型。 */
    private String mediaType;
    /** 内部对象引用；不保存公开 URL。 */
    private String objectKey;
    /** 媒体替代文本。 */
    private String alt;
    /** 媒体描述。 */
    private String description;
}
