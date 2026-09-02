package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.support;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParseStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 已启用文件类型策略的唯一选择器。
 *
 * @author hongqy
 */
@Component
public class DocumentParseStrategyRegistry {

    private final List<DocumentParseStrategy> strategies;

    public DocumentParseStrategyRegistry(List<DocumentParseStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /**
     * 查找已预检文件能力唯一对应的策略。
     *
     * @param capability 上传预检冻结的文件能力
     * @return 唯一匹配策略
     */
    public DocumentParseStrategy require(DocumentFileCapability capability) {
        List<DocumentParseStrategy> matches = strategies.stream()
                .filter(strategy -> strategy.supports(capability))
                .toList();
        if (matches.size() != 1) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_UNSUPPORTED_FILE_TYPE);
        }
        return matches.getFirst();
    }
}
