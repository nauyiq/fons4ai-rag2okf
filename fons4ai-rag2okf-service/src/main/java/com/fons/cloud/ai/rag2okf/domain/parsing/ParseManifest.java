package com.fons.cloud.ai.rag2okf.domain.parsing;

import java.util.List;

/**
 * 解析产物 Manifest，描述一次解析的完整结果。
 *
 * <p>不可变值对象，写入 MinIO 后以数据库指针切换生效。
 * blockCount=0 表示解析失败，调用方应拒绝发布。
 *
 * @param parseRevisionKey  解析 revision 业务标识
 * @param documentVersionKey 文档版本业务标识
 * @param parserProfile     解析器 Profile（NATIVE_TIKA、MINERU_LAYOUT）
 * @param parserTrace       解析器执行轨迹
 * @param blocks            解析产物块列表
 * @param blockCount        块数量
 * @param contentHash       解析内容 SHA-256 摘要
 * @author hongqy
 */
public record ParseManifest(
        String parseRevisionKey,
        String documentVersionKey,
        String parserProfile,
        ParserTrace parserTrace,
        List<ParsedBlock> blocks,
        int blockCount,
        String contentHash
) {
    /**
     * 解析产物块。
     *
     * @param index        块序号，从 0 开始
     * @param content      块文本内容
     * @param sourceAnchor 来源定位，不伪造页码
     */
    public record ParsedBlock(
            int index,
            String content,
            SourceAnchor sourceAnchor
    ) {
    }
}
