package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 已解析文档所对应的源文件安全信息。
 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParsedDocumentSource {
    /**
     * 源文件业务令牌。
     */
    private String fileToken;
    /**
     * 用户上传时的原始文件名。
     */
    private String filename;
    /**
     * 经服务端校验的 MIME 类型。
     */
    private String contentType;
    /**
     * 源文件 SHA-256 摘要。
     */
    private String sha256;
}
