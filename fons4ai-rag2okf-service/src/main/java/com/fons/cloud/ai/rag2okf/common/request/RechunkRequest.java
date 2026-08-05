package com.fons.cloud.ai.rag2okf.common.request;

import com.fons.cloud.ai.rag2okf.domain.parsing.ChunkProfile;

/**
 * 重新分块请求体（技术设计 §3.4）。
 *
 * @param confirmed               必须为 true 才执行
 * @param expectedChunkRevisionKey 调用方持有的当前 ChunkRevision key
 * @param chunkProfile            新分块策略
 * @author hongqy
 */
public record RechunkRequest(
        boolean confirmed,
        String expectedChunkRevisionKey,
        ChunkProfile chunkProfile
) {
}
