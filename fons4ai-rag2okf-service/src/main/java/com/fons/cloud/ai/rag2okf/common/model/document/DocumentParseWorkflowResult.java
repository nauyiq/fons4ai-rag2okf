package com.fons.cloud.ai.rag2okf.common.model.document;

/**
 * 解析技术流水线生成的已校验制品内容。
 *
 * <p>该模型只承载解析、规范化和序列化后的技术结果；对象存储写入、
 * 结果阶段推进与任务状态流转由应用层协调。</p>
 */
public class DocumentParseWorkflowResult {

    /** 通过结构与安全校验的规范化解析文档。 */
    private final ParsedDocument parsedDocument;

    /** ParsedDocument 的稳定 JSON 字节。 */
    private final byte[] parsedDocumentJson;

    /** ParsedDocument 派生的 Markdown 字节。 */
    private final byte[] parsedMarkdown;

    /**
     * 创建一次完整解析技术流水线的输出。
     *
     * @param parsedDocument     已校验解析文档
     * @param parsedDocumentJson 解析文档 JSON
     * @param parsedMarkdown     解析文档 Markdown
     */
    public DocumentParseWorkflowResult(
            ParsedDocument parsedDocument,
            byte[] parsedDocumentJson,
            byte[] parsedMarkdown) {
        this.parsedDocument = parsedDocument;
        this.parsedDocumentJson = parsedDocumentJson.clone();
        this.parsedMarkdown = parsedMarkdown.clone();
    }

    /**
     * 获取已校验解析文档。
     *
     * @return 规范化解析文档
     */
    public ParsedDocument getParsedDocument() {
        return parsedDocument;
    }

    /**
     * 获取解析文档 JSON 的副本。
     *
     * @return 稳定 JSON 字节
     */
    public byte[] getParsedDocumentJson() {
        return parsedDocumentJson.clone();
    }

    /**
     * 获取解析文档 Markdown 的副本。
     *
     * @return Markdown 字节
     */
    public byte[] getParsedMarkdown() {
        return parsedMarkdown.clone();
    }

}
