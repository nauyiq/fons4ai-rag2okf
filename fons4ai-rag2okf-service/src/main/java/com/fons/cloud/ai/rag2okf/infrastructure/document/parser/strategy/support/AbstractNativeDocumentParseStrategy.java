package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentFileCategory;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.document.BuiltInBaseExtractor;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParseStrategy;

/**
 * 使用受控原生 Facade 提取文本的文件类型策略基类。
 *
 * @author hongqy
 */
abstract class AbstractNativeDocumentParseStrategy implements DocumentParseStrategy {

    private final DocumentFileCategory category;
    private final BuiltInBaseExtractor baseExtractor;

    AbstractNativeDocumentParseStrategy(DocumentFileCategory category, BuiltInBaseExtractor baseExtractor) {
        this.category = category;
        this.baseExtractor = baseExtractor;
    }

    @Override
    public boolean supports(DocumentFileCapability capability) {
        return category.matches(capability.category());
    }

    @Override
    public RawParseResult parse(ParseExecutionContext context, DocumentFileCapability capability) {
        return baseExtractor.extractText(context, true);
    }
}
