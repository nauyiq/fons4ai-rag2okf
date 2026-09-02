package com.fons.cloud.ai.rag2okf.common.request.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingMode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 上传新文档请求契约（设计 §3.2 上传契约）。
 *
 * <p>由 Controller 从 multipart 表单解析构造后传入应用服务：
 * {@code processingMode} 表达处理意图（缺省 DEFAULT），{@code parserType}
 * 可省略（缺省 BUILT_IN），{@code chunkPolicy} 可省略（缺省知识库默认，
 * 未配置时 RECURSIVE+PARENT_CHILD）。非法值由应用服务预检拒绝，不做默认回退。</p>
 *
 * <p>请求不携带 folderPath：文档域不实现文件夹管理（BR-016）。</p>
 *
 * @param processingMode 处理模式，必填语义（Controller 缺省 DEFAULT）
 * @param parserType     解析器类型，可空（缺省 BUILT_IN）
 * @param chunkPolicy    分块策略，可空（缺省知识库默认）
 * @author hongqy
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadRequest {

    /** 处理模式，缺省时由 Controller 转换为 DEFAULT。 */
    private ProcessingMode processingMode;

    /** 解析器类型，缺省使用 BUILT_IN。 */
    private ParserType parserType;

    /** 分块策略，缺省使用知识库默认或系统默认。 */
    private ChunkPolicyRequest chunkPolicy;
}
