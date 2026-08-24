package com.fons.cloud.ai.rag2okf.common.request.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingMode;

import java.util.Map;

/**
 * 上传新文档请求契约（设计 §3.2 上传契约）。
 *
 * <p>由 Controller 从 multipart 表单解析构造后传入应用服务：
 * {@code processingMode} 表达处理意图（缺省 DEFAULT），{@code parserType}
 * 可省略（缺省 BUILT_IN），{@code chunkPolicy} 可省略（缺省知识库默认，
 * 未配置时 STRUCTURE+PARENT_CHILD）。非法值由应用服务预检拒绝，不做默认回退。</p>
 *
 * <p>请求不携带 folderPath：文档域不实现文件夹管理（BR-016）。</p>
 *
 * @param processingMode 处理模式，必填语义（Controller 缺省 DEFAULT）
 * @param parserType     解析器类型，可空（缺省 BUILT_IN）
 * @param chunkPolicy    分块策略，可空（缺省知识库默认）
 * @author hongqy
 */
public record DocumentUploadRequest(
        ProcessingMode processingMode,
        ParserType parserType,
        ChunkPolicy chunkPolicy) {

    /**
     * 分块策略请求：边界与层级两维正交（设计 §3.2）。
     *
     * @param boundaryType 分块边界策略：LENGTH、STRUCTURE、SEMANTIC
     * @param hierarchyType 分块层级策略：FLAT、PARENT_CHILD
     * @param parameters    策略非秘密参数，可空
     */
    public record ChunkPolicy(
            String boundaryType,
            String hierarchyType,
            Map<String, Object> parameters) {
    }
}
