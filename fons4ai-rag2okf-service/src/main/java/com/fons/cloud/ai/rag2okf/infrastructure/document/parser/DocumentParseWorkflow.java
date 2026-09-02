package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentParseWorkflowResult;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.common.utils.ParsedDocumentValidator;
import org.springframework.stereotype.Component;

/**
 * 文档解析、规范化和制品编码的基础设施技术流水线。
 *
 * <p>本组件不读取或写入业务状态，不创建任务，也不决定文档生命周期；
 * 它只把受控源流和冻结策略转换为可保存的技术制品。</p>
 */
@Component
public class DocumentParseWorkflow {

    /**
     * 按解析器类型查找并调用具体解析实现的注册表。
     */
    private final DocumentParserRegistry parserRegistry;

    /**
     * 将解析器原始输出转换为统一文档结构的规范化器。
     */
    private final ParsedDocumentNormalizer normalizer;

    /**
     * ParsedDocument 的稳定 JSON 与 Markdown 编码器。
     */
    private final ParsedDocumentCodec parsedDocumentCodec;

    /**
     * 创建解析技术流水线。
     *
     * @param parserRegistry      解析器注册表
     * @param normalizer          解析结果规范化器
     * @param parsedDocumentCodec 解析文档编码器
     */
    public DocumentParseWorkflow(
            DocumentParserRegistry parserRegistry,
            ParsedDocumentNormalizer normalizer,
            ParsedDocumentCodec parsedDocumentCodec) {
        this.parserRegistry = parserRegistry;
        this.normalizer = normalizer;
        this.parsedDocumentCodec = parsedDocumentCodec;
    }

    /**
     * 判断指定解析器是否可用，供任务创建前的快速预检使用。
     *
     * @param parserType 解析器类型
     * @return 已注册且启用时返回 {@code true}
     */
    public boolean isEnabled(ParserType parserType) {
        return parserRegistry.isEnabled(parserType);
    }

    /**
     * 执行解析技术流水线并生成可持久化 ParsedDocument 制品。
     *
     * @param context 受控源文件和冻结的解析上下文
     * @return 已校验、已编码的解析制品
     */
    public DocumentParseWorkflowResult process(ParseExecutionContext context) {
        // 步骤 1：调用任务冻结的解析器，得到与具体格式相关的原始解析结果。
        RawParseResult rawResult = parserRegistry.requireEnabled(context.parserType()).parse(context);

        // 步骤 2：统一为 ParsedDocument 并校验结构、来源锚点及敏感字段约束。
        ParsedDocument parsedDocument = normalizer.normalize(context, rawResult);
        ParsedDocumentValidator.validate(parsedDocument);

        // 步骤 3：编码为稳定字节，交由 Application 决定何时写入对象存储。
        return new DocumentParseWorkflowResult(
                parsedDocument, parsedDocumentCodec.writeJson(parsedDocument), parsedDocumentCodec.writeMarkdown(parsedDocument));
    }
}
