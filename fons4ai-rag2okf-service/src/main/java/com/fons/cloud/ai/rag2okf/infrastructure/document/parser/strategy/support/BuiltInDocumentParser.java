package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserAvailability;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.BuiltInCapabilityPlanner;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.BuiltInFileCapabilityCatalog;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParser;
import org.springframework.stereotype.Component;

/**
 * Built-in 解析器。
 *
 * <p>按文件能力清单执行确定性基础提取，再由能力规划器执行必要能力和可选增强。
 * 必要能力失败时不返回空结果，也不回退其他解析器。</p>
 *
 * @author hongqy
 */
@Component
public class BuiltInDocumentParser implements DocumentParser {

    private final BuiltInFileCapabilityCatalog capabilityCatalog;
    private final DocumentParseStrategyRegistry strategyRegistry;
    private final BuiltInCapabilityPlanner capabilityPlanner;

    public BuiltInDocumentParser(
            BuiltInFileCapabilityCatalog capabilityCatalog,
            DocumentParseStrategyRegistry strategyRegistry,
            BuiltInCapabilityPlanner capabilityPlanner) {
        this.capabilityCatalog = capabilityCatalog;
        this.strategyRegistry = strategyRegistry;
        this.capabilityPlanner = capabilityPlanner;
    }

    @Override
    public ParserType type() {
        return ParserType.BUILT_IN;
    }

    @Override
    public ParserAvailability availability() {
        return ParserAvailability.ENABLED;
    }

    @Override
    public RawParseResult parse(ParseExecutionContext context) {
        if (context == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        var capability = capabilityCatalog.require(context.filename(), context.contentType());
        RawParseResult baseResult = strategyRegistry.require(capability).parse(context, capability);
        return capabilityPlanner.enhance(context, capability, baseResult);
    }
}
