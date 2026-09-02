package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserAvailability;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.DocumentParserRegistry;

/**
 * 文档解析器策略契约。
 *
 * <p>解析器多实现以普通应用策略方式注册到 {@link DocumentParserRegistry}，不构成独立 Port 层。
 * 本期启用 {@link ParserType#BUILT_IN}；{@link ParserType#MINERU} 仅保留扩展契约和禁用适配器骨架。</p>
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>{@link #availability()} 返回 {@link ParserAvailability#DISABLED} 的解析器，
 *       其 {@link #parse(ParseExecutionContext)} 必须在任何 IO（任务、MinIO、模型、网络）前抛出
 *       {@code PARSER_NOT_AVAILABLE}。</li>
 *   <li>注册表禁止 fallback：请求未启用解析器时直接失败，不回退到其他解析器。</li>
 * </ul>
 *
 * @author hongqy
 */
public interface DocumentParser {

    /**
     * 返回解析器类型。
     *
     * @return 解析器类型枚举
     */
    ParserType type();

    /**
     * 返回解析器可用性。
     *
     * <p>注册表据此判断是否允许进入实际解析链路；{@link ParserAvailability#DISABLED} 表示
     * 仅保留扩展契约，请求该解析器必须失败。</p>
     *
     * @return 解析器可用性
     */
    ParserAvailability availability();

    /**
     * 执行解析。
     *
     * <p>禁用解析器必须在此方法入口抛出 {@code PARSER_NOT_AVAILABLE}，不进行任何 IO。
     * 启用解析器的具体行为在 TP-003 实现。</p>
     *
     * @param context 解析执行上下文，承载源文件指针和冻结快照
     * @return 解析器原始返回结果
     */
    RawParseResult parse(ParseExecutionContext context);
}
