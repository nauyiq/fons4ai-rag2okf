package com.fons.cloud.ai.rag2okf.parsing;

import com.fons.cloud.ai.rag2okf.common.dto.ParsingChunkProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ParsingChunkProfile 值对象测试。
 *
 * @author hongqy
 */
@DisplayName("ParsingChunkProfile 分块策略")
class ChunkProfileTest {

    @Test
    @DisplayName("默认递归分块策略为 recursive/1000/100")
    void defaultRecursive_hasCorrectValues() {
        ParsingChunkProfile profile = ParsingChunkProfile.DEFAULT_RECURSIVE;

        assertEquals(ParsingChunkProfile.RECURSIVE, profile.strategy());
        assertEquals(1000, profile.chunkSize());
        assertEquals(100, profile.overlap());
    }

    @Test
    @DisplayName("markdownHeader 创建 markdown-header 策略")
    void markdownHeader_createsCorrectStrategy() {
        ParsingChunkProfile profile = ParsingChunkProfile.markdownHeader(800, 80);

        assertEquals(ParsingChunkProfile.MARKDOWN_HEADER, profile.strategy());
        assertEquals(800, profile.chunkSize());
        assertEquals(80, profile.overlap());
    }

    @Test
    @DisplayName("策略常量值稳定")
    void strategyConstants_areStable() {
        assertEquals("recursive", ParsingChunkProfile.RECURSIVE);
        assertEquals("markdown-header", ParsingChunkProfile.MARKDOWN_HEADER);
    }
}
