package com.fons.cloud.ai.rag2okf.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactScope;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.StoredArtifact;
import com.fons.cloud.ai.rag2okf.common.dto.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.dto.ParsingChunkProfile;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentChunkerPort;
import com.fons.cloud.ai.rag2okf.common.dto.ParseManifest;
import com.fons.cloud.ai.rag2okf.common.dto.ParserTrace;
import com.fons.cloud.ai.rag2okf.common.dto.SourceAnchor;
import com.fons.cloud.ai.rag2okf.infrastructure.parsing.LangChain4jChunkerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LangChain4jChunkerAdapter 测试。
 *
 * <p>覆盖 AC-012（parent/child 分块）和 AC-014（空产物拒绝）。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("文档分块适配器")
class LangChain4jChunkerAdapterTest {

    @Mock private DocumentArtifactStore artifactStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private LangChain4jChunkerAdapter adapter;

    @BeforeEach
    void setUp() {
        try {
            var field = LangChain4jChunkerAdapter.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(adapter, objectMapper);
        } catch (Exception e) {
            fail("无法注入 ObjectMapper: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("recursive 策略分块成功，生成 child chunk 并写入 MinIO")
    void chunk_recursiveProducesChunks() {
        ArtifactScope scope = new ArtifactScope("01J_WS", "01J_KB", "01J_DOC");

        // 构造包含多段文本的 ParseManifest
        ParseManifest parseManifest = buildParseManifest("01J_PARSE", "01J_VER",
                "段落一内容较长用于测试分块。" .repeat(50) + "\n\n"
                        + "段落二内容同样较长。" .repeat(50));
        mockReadParseManifest(scope, "01J_PARSE", parseManifest);

        when(artifactStore.storeManifest(any())).thenReturn(
                new StoredArtifact("chunk/key", "def456", 200L, Map.of()));

        DocumentChunkerPort.ChunkRequest request = new DocumentChunkerPort.ChunkRequest(
                scope, "01J_PARSE", "01J_CHUNK", ParsingChunkProfile.DEFAULT_RECURSIVE);

        DocumentChunkerPort.ChunkResult result = adapter.chunk(request);

        assertNotNull(result);
        ChunkManifest manifest = result.manifest();
        assertEquals("01J_CHUNK", manifest.chunkRevisionKey());
        assertEquals("01J_PARSE", manifest.parseRevisionKey());
        assertTrue(manifest.childCount() > 0, "recursive 分块必须产生子块");
        assertFalse(manifest.chunks().isEmpty(), "分块列表不可为空");
        assertNotNull(manifest.contentHash(), "contentHash 不可为 null");

        // 验证 MinIO 写入被调用
        verify(artifactStore).storeManifest(any());
    }

    @Test
    @DisplayName("markdown-header 策略分块成功，生成 parent/child 结构")
    void chunk_markdownHeaderProducesParentChild() {
        ArtifactScope scope = new ArtifactScope("01J_WS", "01J_KB", "01J_DOC");

        String markdown = "# 标题一\n\n" + "正文内容。" .repeat(100) + "\n\n"
                + "## 子标题\n\n" + "子标题正文。" .repeat(100) + "\n\n"
                + "# 标题二\n\n" + "第二段正文。" .repeat(100);
        ParseManifest parseManifest = buildParseManifest("01J_PARSE", "01J_VER", markdown);
        mockReadParseManifest(scope, "01J_PARSE", parseManifest);

        when(artifactStore.storeManifest(any())).thenReturn(
                new StoredArtifact("chunk/key", "def456", 200L, Map.of()));

        DocumentChunkerPort.ChunkRequest request = new DocumentChunkerPort.ChunkRequest(
                scope, "01J_PARSE", "01J_CHUNK",
                ParsingChunkProfile.markdownHeader(500, 50));

        DocumentChunkerPort.ChunkResult result = adapter.chunk(request);

        assertNotNull(result);
        ChunkManifest manifest = result.manifest();
        assertTrue(manifest.childCount() > 0, "markdown-header 分块必须产生子块");
        assertEquals(ParsingChunkProfile.MARKDOWN_HEADER, manifest.chunkProfile().strategy());
    }

    @Test
    @DisplayName("分块产物为空时抛出异常，不伪造结果")
    void chunk_emptyResultThrowsException() {
        ArtifactScope scope = new ArtifactScope("01J_WS", "01J_KB", "01J_DOC");

        // 构造空 blocks 的 ParseManifest（模拟解析失败但 manifest 存在的场景）
        ParseManifest parseManifest = new ParseManifest(
                "01J_PARSE", "01J_DOC", "NATIVE_TIKA",
                new ParserTrace("native", 1L, "MARKDOWN", "DEFAULT 选型"),
                List.of(), 0, "sha256:empty");
        mockReadParseManifest(scope, "01J_PARSE", parseManifest);

        DocumentChunkerPort.ChunkRequest request = new DocumentChunkerPort.ChunkRequest(
                scope, "01J_PARSE", "01J_CHUNK", ParsingChunkProfile.DEFAULT_RECURSIVE);

        // AC-014：空产物拒绝，不伪造
        assertThrows(DocumentArtifactException.class, () -> adapter.chunk(request));
    }

    /**
     * 构造包含单个文本块的 ParseManifest。
     */
    private ParseManifest buildParseManifest(String parseRevisionKey, String documentKey,
                                             String content) {
        List<ParseManifest.ParsedBlock> blocks = new ArrayList<>();
        blocks.add(new ParseManifest.ParsedBlock(0, content, SourceAnchor.none()));
        return new ParseManifest(
                parseRevisionKey, documentKey, "NATIVE_TIKA",
                new ParserTrace("native", 1L, "MARKDOWN", "DEFAULT 选型"),
                blocks, 1, "sha256:test");
    }

    /**
     * 模拟从 MinIO 读取 ParseManifest JSON。
     */
    private void mockReadParseManifest(ArtifactScope scope, String parseRevisionKey,
                                       ParseManifest manifest) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(manifest);
            when(artifactStore.open(any())).thenReturn(
                    new ArtifactContent(new ByteArrayInputStream(json)));
        } catch (Exception e) {
            fail("序列化 ParseManifest 失败: " + e.getMessage());
        }
    }
}
