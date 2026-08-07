package com.fons.cloud.ai.rag2okf.common.dto;

/**
 * 解析器执行轨迹，记录解析过程的可观测信息。
 *
 * @param provider        解析器 provider 标识（如 native、mineru）
 * @param durationMillis  解析耗时毫秒
 * @param outputFormat    输出格式（如 MARKDOWN、PLAIN_TEXT）
 * @param selectionReason 选型原因
 * @author hongqy
 */
public record ParserTrace(
        String provider,
        long durationMillis,
        String outputFormat,
        String selectionReason
) {
}
