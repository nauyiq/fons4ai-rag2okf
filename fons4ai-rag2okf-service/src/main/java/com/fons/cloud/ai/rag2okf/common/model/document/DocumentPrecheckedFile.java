package com.fons.cloud.ai.rag2okf.common.model.document;

import java.io.InputStream;

import lombok.Getter;

/**
 * 通过安全预检的受控上传文件。
 *
 * <p>输入流已恢复读取魔数前的完整内容，使用方负责关闭该流。</p>
 */
@Getter
public class DocumentPrecheckedFile {
    /** 经安全策略净化后的文件名。 */
    private final String filename;

    /** 服务端根据文件能力清单确认的 MIME 类型。 */
    private final String contentType;

    /** 已推回魔数字节的完整内容流，调用方负责关闭。 */
    private final InputStream inputStream;

    /**
     * 创建已通过预检的文件内容。
     *
     * @param filename    经净化的文件名
     * @param contentType 服务端确认的 MIME 类型
     * @param inputStream 可从文件首字节读取的内容流
     */
    public DocumentPrecheckedFile(String filename, String contentType, InputStream inputStream) {
        this.filename = filename;
        this.contentType = contentType;
        this.inputStream = inputStream;
    }
}
