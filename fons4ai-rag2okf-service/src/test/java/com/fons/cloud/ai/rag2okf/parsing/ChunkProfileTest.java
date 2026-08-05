package com.fons.cloud.ai.rag2okf.parsing;

import com.fons.cloud.ai.rag2okf.domain.parsing.ChunkProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChunkProfile 值对象测试。
 *
 * @author hongqy
 */
@DisplayName("ChunkProfile 分块策略")
class ChunkProfileTest {

    @Test
    @DisplayName("默认递归分块策略为 recursive/1000/100")
    void defaultRecursive_hasCorrectValues() {
        ChunkProfile profile = ChunkProfile.DEFAULT_RECURSIVE;

        assertEquals(ChunkProfile.RECURSIVE, profile.strategy());
        assertEquals(1000, profile.chunkSize());
        assertEquals(100, profile.overlap());
    }

    @Test
    @DisplayName("markdownHeader 创建 markdown-header 策略")
    void markdownHeader_createsCorrectStrategy() {
        ChunkProfile profile = ChunkProfile.markdownHeader(800, 80);

        assertEquals(ChunkProfile.MARKDOWN_HEADER, profile.strategy());
        assertEquals(800, profile.chunkSize());
        assertEquals(80, profile.overlap());
    }

    @Test
    @DisplayName("策略常量值稳定")
    void strategyConstants_areStable() {
        assertEquals("recursive", ChunkProfile.RECURSIVE);
        assertEquals("markdown-header", ChunkProfile.MARKDOWN_HEADER);
    }
}
