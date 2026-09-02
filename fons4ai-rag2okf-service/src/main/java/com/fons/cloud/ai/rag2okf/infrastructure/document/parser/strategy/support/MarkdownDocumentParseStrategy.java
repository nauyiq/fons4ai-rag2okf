package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentFileCategory;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.document.BuiltInBaseExtractor;
import org.springframework.stereotype.Component;

/**
 * Markdown 文件的确定性提取策略。
 *
 * @author hongqy
 */
@Component
public class MarkdownDocumentParseStrategy extends AbstractNativeDocumentParseStrategy {

    public MarkdownDocumentParseStrategy(BuiltInBaseExtractor baseExtractor) {
        super(DocumentFileCategory.MARKDOWN, baseExtractor);
    }
}
