package com.fons.cloud.ai.rag2okf.common.model.document;

/**
 * 解析执行上下文。
 *
 * <p>由解析任务执行器在调用 {@code DocumentParser.parse} 前构造，承载源文件指针、
 * 冻结快照和能力计划等解析所需输入。本期为解析器策略契约占位接口，
 * 具体字段在 TP-003 Built-in 解析实现时补齐；禁用解析器不读取任何上下文内容。</p>
 *
 * @author hongqy
 */
public interface ParseExecutionContext {
}
