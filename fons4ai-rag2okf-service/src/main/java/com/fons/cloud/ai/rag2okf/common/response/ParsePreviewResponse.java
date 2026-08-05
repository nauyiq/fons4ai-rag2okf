package com.fons.cloud.ai.rag2okf.common.response;

import com.fons.cloud.ai.rag2okf.domain.parsing.ParserTrace;
import com.fons.cloud.ai.rag2okf.domain.parsing.SourceAnchor;

import java.util.List;

/**
 * 解析预览响应，返回结构化 block 与 SourceAnchor 分页（AC-012、AC-013）。
 *
 * <p>无当前 ParseRevision 时返回空 blocks 和 hasParse=false（AC-013 不伪造结果）。
 *
 * @param hasParse    是否存在已成功的解析产物
 * @param parserProfile 解析器 Profile
 * @param parserTrace 解析器执行轨迹
 * @param blockCount  解析块总数
 * @param contentHash 解析产物内容摘要
 * @param blocks      结构化解析块列表
 * @author hongqy
 */
public record ParsePreviewResponse(
        boolean hasParse,
        String parserProfile,
        ParserTrace parserTrace,
        int blockCount,
        String contentHash,
        List<ParsedBlockView> blocks
) {

    /**
     * 解析块视图。
     *
     * @param index        块序号
     * @param content      块文本内容
     * @param sourceAnchor 来源锚点
     */
    public record ParsedBlockView(
            int index,
            String content,
            SourceAnchor sourceAnchor
    ) {
    }
}
