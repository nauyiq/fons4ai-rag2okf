package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserAvailability;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParser;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 解析器策略注册表。
 *
 * <p>按 {@link ParserType} 注册解析器实例，{@link #requireEnabled(ParserType)} 返回启用解析器
 * 或抛出 {@code PARSER_NOT_AVAILABLE}。注册表禁止 fallback：请求未登记或
 * {@link ParserAvailability#DISABLED} 的解析器时直接失败，不回退到其他解析器。</p>
 *
 * <h3>注册规则</h3>
 * <ul>
 *   <li>同一 {@link ParserType} 只允许注册一次，重复注册抛出 {@link IllegalStateException}。</li>
 *   <li>禁止注册 null 解析器或 null 类型。</li>
 * </ul>
 *
 * <p>本期启用 {@link ParserType#BUILT_IN}；{@link ParserType#MINERU} 注册禁用骨架，
 * 任何 {@code requireEnabled(MINERU)} 调用在任何任务、MinIO、模型或网络调用前失败。</p>
 *
 * @author hongqy
 */
@Component
public class DocumentParserRegistry {

    /** 按 ParserType 索引的解析器实例。 */
    private final Map<ParserType, DocumentParser> parsersByType;

    /**
     * 创建解析器注册表。
     *
     * @param parsers 要注册的解析器实例，不可为 null，不可包含 null 元素
     */
    public DocumentParserRegistry(DocumentParser... parsers) {
        this.parsersByType = register(parsers);
    }

    /**
     * 创建解析器注册表。
     *
     * @param parsers 要注册的解析器实例集合，不可为 null，不可包含 null 元素
     */
    public DocumentParserRegistry(Collection<DocumentParser> parsers) {
        Objects.requireNonNull(parsers, "parsers must not be null");
        this.parsersByType = register(parsers.toArray(new DocumentParser[0]));
    }

    /**
     * 返回启用解析器或抛出 {@code PARSER_NOT_AVAILABLE}。
     *
     * <p>查找顺序：按显式 {@link ParserType} 查找→检查 {@link ParserAvailability}。
     * 未登记或 {@link ParserAvailability#DISABLED} 的解析器直接失败，不回退。</p>
     *
     * @param type 解析器类型，不可为 null
     * @return 启用状态的解析器实例
     * @throws DocumentProcessingException 当解析器未登记或处于 DISABLED 状态时，
     *         携带 {@link Rag2OkfResultCode#PARSER_NOT_AVAILABLE}
     */
    public DocumentParser requireEnabled(ParserType type) {
        Objects.requireNonNull(type, "parserType must not be null");
        DocumentParser parser = parsersByType.get(type);
        if (parser == null || parser.availability() == ParserAvailability.DISABLED) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSER_NOT_AVAILABLE);
        }
        return parser;
    }

    /**
     * 返回解析器是否处于启用状态，不抛出异常。
     *
     * <p>供应用服务做解析器可用性预检时直接判断并返回 {@code R.failed}，
     * 避免为了一个布尔结果引入异常流。</p>
     *
     * @param type 解析器类型，不可为 null
     * @return {@code true} 表示已登记且启用，{@code false} 表示未登记或处于 DISABLED 状态
     */
    public boolean isEnabled(ParserType type) {
        Objects.requireNonNull(type, "parserType must not be null");
        DocumentParser parser = parsersByType.get(type);
        return parser != null && parser.availability() == ParserAvailability.ENABLED;
    }

    /**
     * 注册解析器并构建索引。
     *
     * @param parsers 解析器数组
     * @return 按 ParserType 索引的不可变映射
     */
    private static Map<ParserType, DocumentParser> register(DocumentParser[] parsers) {
        Map<ParserType, DocumentParser> map = new HashMap<>();
        for (DocumentParser parser : parsers) {
            Objects.requireNonNull(parser, "parser must not be null");
            ParserType type = parser.type();
            Objects.requireNonNull(type, "parser type must not be null");
            if (map.containsKey(type)) {
                throw new IllegalStateException("Duplicate parser registration for type: " + type);
            }
            map.put(type, parser);
        }
        return Map.copyOf(map);
    }
}
