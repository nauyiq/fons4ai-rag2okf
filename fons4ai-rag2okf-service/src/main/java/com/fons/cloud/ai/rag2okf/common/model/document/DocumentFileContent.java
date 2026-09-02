package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.Getter;
import java.io.InputStream;

/** 受控下载的源文件内容，调用方负责关闭输入流。 */
@Getter
public class DocumentFileContent {
    /** 原始文件名。 */ private final String filename;
    /** MIME 类型。 */ private final String contentType;
    /** 文件字节数。 */ private final long size;
    /** 文件输入流。 */ private final InputStream inputStream;
    public DocumentFileContent(String filename, String contentType, long size, InputStream inputStream) {
        this.filename = filename; this.contentType = contentType; this.size = size; this.inputStream = inputStream;
    }
}
