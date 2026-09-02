package com.fons.cloud.ai.rag2okf.common.model.document;

import java.io.InputStream;

/**
 * 受控源制品的可重复打开读取契约。
 *
 * <p>每次调用都必须返回从文件首字节开始的新流，调用方负责关闭。该契约不暴露对象键、
 * 临时路径或访问凭据，使 PDF 探测、原生提取和 OCR 可以各自独立读取同一源制品。</p>
 *
 * @author hongqy
 */
@FunctionalInterface
public interface DocumentSourceStreamProvider {

    /**
     * 打开一条从源文件首字节开始的读取流。
     *
     * @return 新的受控源文件输入流，调用方负责关闭
     */
    InputStream openStream();
}
