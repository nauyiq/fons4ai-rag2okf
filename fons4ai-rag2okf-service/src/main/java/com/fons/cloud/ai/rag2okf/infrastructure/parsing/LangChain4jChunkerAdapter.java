package com.fons.cloud.ai.rag2okf.infrastructure.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ArtifactReference;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ArtifactType;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.ManifestArtifactRequest;
import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore.StoredArtifact;
import com.fons.cloud.ai.rag2okf.domain.parsing.ChunkManifest;
import com.fons.cloud.ai.rag2okf.domain.parsing.ChunkManifest.Chunk;
import com.fons.cloud.ai.rag2okf.domain.parsing.ChunkProfile;
import com.fons.cloud.ai.rag2okf.domain.parsing.DocumentChunkerPort;
import com.fons.cloud.ai.rag2okf.domain.parsing.ParseManifest;
import com.fons.cloud.ai.rag.langchain.document.LangChain4jDocumentSplitter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j 文档分块适配器。
 *
 * <p>适配 Fons4AI LangChain4j Splitter 为 rag2okf 领域端口 {@link DocumentChunkerPort}。
 * 支持 recursive 和 markdown-header 两种策略，后者内置 Parent/Child 组装。
 *
 * @author hongqy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangChain4jChunkerAdapter implements DocumentChunkerPort {

    private final DocumentArtifactStore artifactStore;
    private final ObjectMapper objectMapper;

    @Override
    public ChunkResult chunk(ChunkRequest request) {
        // 1. 从 MinIO 读取 ParseManifest
        ParseManifest parseManifest = readParseManifest(request.scope(), request.parseRevisionKey());

        // AC-014：输入 manifest 为空时直接拒绝，不伪造分块结果
        if (parseManifest.blockCount() == 0 || parseManifest.blocks().isEmpty()) {
            throw new DocumentArtifactException(
                    "解析产物为空，拒绝分块: " + request.parseRevisionKey());
        }

        // 2. 重建 LangChain4j Document
        Document document = reconstructDocument(parseManifest);

        // 3. 构造分块器
        LangChain4jDocumentSplitter splitter = createSplitter(request.chunkProfile());

        // 4. 分块
        List<TextSegment> segments = splitter.split(document);

        // 5. 转换为 ChunkManifest
        ChunkManifest manifest = toChunkManifest(request, segments);

        if (manifest.childCount() == 0) {
            throw new DocumentArtifactException(
                    "分块产物为空: " + request.parseRevisionKey());
        }

        // 6. 写入 MinIO
        StoredArtifact manifestArtifact = writeManifest(
                request.scope(), request.chunkRevisionKey(), manifest);

        log.info("Chunk completed: parseRevisionKey={}, chunkRevisionKey={}, chunks={}",
                request.parseRevisionKey(), request.chunkRevisionKey(), manifest.childCount());

        return new ChunkResult(manifest, manifestArtifact);
    }

    private ParseManifest readParseManifest(
            DocumentArtifactStore.ArtifactScope scope, String parseRevisionKey) {
        ArtifactReference ref = new ArtifactReference(
                scope, ArtifactType.PARSED_MANIFEST, parseRevisionKey);
        ArtifactContent content = artifactStore.open(ref);
        try {
            return objectMapper.readValue(content.inputStream(), ParseManifest.class);
        } catch (IOException e) {
            throw new DocumentArtifactException("读取 ParseManifest 失败: " + parseRevisionKey, e);
        } finally {
            try {
                content.inputStream().close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }

    /**
     * 从 ParseManifest 重建 LangChain4j Document。
     *
     * <p>将所有块内容按双换行拼接，保留 metadata 中的解析器信息。
     */
    private Document reconstructDocument(ParseManifest manifest) {
        StringBuilder text = new StringBuilder();
        for (ParseManifest.ParsedBlock block : manifest.blocks()) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(block.content());
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("parseRevisionKey", manifest.parseRevisionKey());
        metadata.put("parserProfile", manifest.parserProfile());
        return Document.from(text.toString(), dev.langchain4j.data.document.Metadata.from(metadata));
    }

    private LangChain4jDocumentSplitter createSplitter(ChunkProfile profile) {
        if (profile == null) {
            return new LangChain4jDocumentSplitter(
                    ChunkProfile.DEFAULT_RECURSIVE.chunkSize(),
                    ChunkProfile.DEFAULT_RECURSIVE.overlap());
        }
        if (ChunkProfile.MARKDOWN_HEADER.equals(profile.strategy())) {
            return new LangChain4jDocumentSplitter(
                    ChunkProfile.MARKDOWN_HEADER,
                    profile.chunkSize(),
                    profile.overlap(),
                    3);
        }
        return new LangChain4jDocumentSplitter(profile.chunkSize(), profile.overlap());
    }

    private ChunkManifest toChunkManifest(ChunkRequest request, List<TextSegment> segments) {
        List<Chunk> chunks = new ArrayList<>();
        int parentCount = 0;
        int childCount = 0;

        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            Map<String, Object> metadata = extractMetadata(segment);
            String parentChunkId = (String) metadata.get("parentChunkId");
            boolean skipEmbedding = Boolean.TRUE.equals(metadata.get("skipEmbedding"));

            if (parentChunkId == null) {
                parentCount++;
            }
            childCount++;

            chunks.add(new Chunk(i, segment.text(), parentChunkId, skipEmbedding, metadata));
        }

        String contentHash = sha256(segments.stream()
                .map(TextSegment::text)
                .reduce("", (a, b) -> a + b));

        return new ChunkManifest(
                request.chunkRevisionKey(),
                request.parseRevisionKey(),
                request.chunkProfile(),
                parentCount,
                childCount,
                chunks,
                contentHash
        );
    }

    /**
     * 从 TextSegment metadata 提取 Parent/Child 信息。
     *
     * <p>使用 getString 安全提取已知 key，不依赖 asMap() 方法。
     */
    private Map<String, Object> extractMetadata(TextSegment segment) {
        Map<String, Object> result = new HashMap<>();
        try {
            var metadata = segment.metadata();
            if (metadata == null) {
                return result;
            }
            // 逐个安全提取已知 key，不伪造
            putIfPresent(result, "chunkId", metadata);
            putIfPresent(result, "parentChunkId", metadata);
            putIfPresent(result, "title", metadata);
            putIfPresent(result, "subtitle", metadata);
            putIfPresent(result, "headerLevel", metadata);
            putIfPresent(result, "skipEmbedding", metadata);
        } catch (Exception e) {
            log.debug("提取 TextSegment metadata 失败，使用空 metadata", e);
        }
        return result;
    }

    /**
     * 安全地从 Metadata 提取字符串值。
     */
    private void putIfPresent(Map<String, Object> result, String key,
                              dev.langchain4j.data.document.Metadata metadata) {
        try {
            String value = metadata.getString(key);
            if (value != null && !value.isBlank()) {
                result.put(key, value);
            }
        } catch (Exception ignored) {
            // key 不存在或类型不匹配，跳过
        }
    }

    private StoredArtifact writeManifest(
            DocumentArtifactStore.ArtifactScope scope, String revisionKey, Object manifest) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(manifest);
            try (InputStream is = new ByteArrayInputStream(json)) {
                return artifactStore.storeManifest(new ManifestArtifactRequest(
                        scope, ArtifactType.CHUNK_MANIFEST, revisionKey, is));
            }
        } catch (IOException e) {
            throw new DocumentArtifactException("序列化 ChunkManifest 失败: " + revisionKey, e);
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "sha256:unavailable";
        }
    }
}
