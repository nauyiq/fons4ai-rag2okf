package com.fons.cloud.ai.rag2okf.infrastructure.document.ocr;

import com.fons.cloud.ai.rag2okf.common.model.document.DocumentSourceStreamProvider;

import java.io.InputStream;
import java.util.Objects;

/**
 * OCR Gateway 的单文件输入。
 *
 * <p>文件内容以可重复打开的受控源流提供。Gateway 不在 Rag2OKF 层复制完整文件，
 * 由下游能力的安全边界决定可发送的最大文件大小。</p>
 *
 * @param fileName 原始文件名，仅用于下游文件类型校验和安全上传名称
 * @param sourceStreamProvider 可重复打开源文件流的供应器
 * @author hongqy
 */
public record OcrDocumentRequest(String fileName, DocumentSourceStreamProvider sourceStreamProvider) {

    /** 校验基础输入。 */
    public OcrDocumentRequest {
        Objects.requireNonNull(fileName, "文件名不可为空");
        Objects.requireNonNull(sourceStreamProvider, "源文件流不可为空");
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不可为空白");
        }
    }

    /**
     * 打开一条从源文件首字节开始的读取流。
     *
     * @return 新的源文件输入流，调用方负责关闭
     */
    public InputStream openSourceStream() {
        return Objects.requireNonNull(sourceStreamProvider.openStream(), "源文件流不可为空");
    }
}
