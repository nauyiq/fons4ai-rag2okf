package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseIntent;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParser;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 根据解析意图选择唯一的启用解析器。
 *
 * <p>路由器不自行猜测或替换 Parser；注册表发现未登记或禁用的目标后，必须以
 * {@code PARSER_NOT_AVAILABLE} 失败。</p>
 *
 * @author hongqy
 */
@Component
public class ParserRouter {

    private final DocumentParserRegistry parserRegistry;

    /**
     * 创建 Parser 路由器。
     *
     * @param parserRegistry 解析器注册表
     */
    public ParserRouter(DocumentParserRegistry parserRegistry) {
        this.parserRegistry = parserRegistry;
    }

    /**
     * 查找解析意图指定的唯一启用解析器。
     *
     * @param intent 已完成文件识别的解析意图
     * @return 与意图一致的已启用解析器
     * @throws DocumentProcessingException 意图为空或目标 Parser 不可用时抛出稳定错误
     */
    public DocumentParser require(ParseIntent intent) {
        if (intent == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        return parserRegistry.requireEnabled(Objects.requireNonNull(intent.parserType(), "parserType must not be null"));
    }
}
