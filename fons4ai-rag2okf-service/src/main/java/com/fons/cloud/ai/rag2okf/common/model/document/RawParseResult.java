package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析器输出的临时中间结果。
 *
 * <p>该对象只在解析策略、规范化器和增强策略之间传递，不写入 MinIO、不映射 HTTP 响应。
 * 它不携带文档业务标识、任务状态或存储路径；这些平台事实由后续规范化过程统一补齐。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RawParseResult {

    /** 解析器识别的标题；无法识别时为空。 */
    private String title;

    /** 解析器识别的内容语言；无法识别时为空。 */
    private String language;

    /** 文档页数；非分页文件或无法可靠识别时为空。 */
    private Integer pageCount;

    /** 音频或视频时长，单位毫秒；非媒体文件时为空。 */
    private Long durationMs;

    /** 按源顺序排列的原始内容块。 */
    private List<RawParseBlock> blocks = new ArrayList<>();

    /** 解析过程的非致命警告，不得包含正文、凭证或厂商原始响应。 */
    private List<String> warnings = new ArrayList<>();

    /** 可观测性追踪步骤，只保存安全化的执行摘要。 */
    private List<ParseTraceStep> trace = new ArrayList<>();

    /**
     * 基于当前元数据替换块、警告和执行轨迹。
     *
     * @param newBlocks 新的有序原始块
     * @param newWarnings 新的非致命警告
     * @param newTrace 新的安全化执行轨迹
     * @return 保留文档元数据的新结果
     */
    public RawParseResult withBlocksAndEvidence(
            List<RawParseBlock> newBlocks, List<String> newWarnings,
            List<ParseTraceStep> newTrace) {
        return new RawParseResult(title, language, pageCount, durationMs,
                new ArrayList<>(newBlocks), new ArrayList<>(newWarnings), new ArrayList<>(newTrace));
    }
}
