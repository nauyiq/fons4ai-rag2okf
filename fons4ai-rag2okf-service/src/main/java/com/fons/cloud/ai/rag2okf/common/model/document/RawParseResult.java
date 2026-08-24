package com.fons.cloud.ai.rag2okf.common.model.document;

/**
 * 解析器原始返回结果。
 *
 * <p>由 {@code DocumentParser.parse} 产出，交由规范化器生成最终 {@code ParsedDocument}。
 * 本期为解析器策略契约占位接口，具体字段在 TP-003 Built-in 解析实现时补齐；
 * 禁用解析器不会产出任何结果。</p>
 *
 * @author hongqy
 */
public interface RawParseResult {
}
