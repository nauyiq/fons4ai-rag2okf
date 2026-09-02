package com.fons.cloud.ai.rag2okf.infrastructure.document.ocr;

/**
 * 文档解析使用的受控 OCR 外部协作入口。
 *
 * <p>Gateway 只负责调用已明确选择的 Provider 并返回页级事实，不决定 OCR 计划、解析器选择、
 * ParsedDocument 规范化或 RAG 分块。</p>
 *
 * @author hongqy
 */
public interface OcrGateway {

    /**
     * 解析一个文件并返回有序页结果。
     *
     * @param request 待解析文件，不包含对象键、凭据或其他任务快照信息
     * @return OCR 页级结果
     */
    OcrDocumentResult parse(OcrDocumentRequest request);
}
