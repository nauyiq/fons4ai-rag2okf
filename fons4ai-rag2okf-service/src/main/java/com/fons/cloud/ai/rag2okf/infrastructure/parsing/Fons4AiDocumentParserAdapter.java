package com.fons.cloud.ai.rag2okf.infrastructure.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactContent;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactReference;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactType;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ManifestArtifactRequest;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.StoredArtifact;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentParserPort;
import com.fons.cloud.ai.rag2okf.common.dto.ParseManifest;
import com.fons.cloud.ai.rag2okf.common.dto.ParseManifest.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.dto.ParserTrace;
import com.fons.cloud.ai.rag2okf.common.dto.SourceAnchor;
import com.fons.cloud.ai.rag.common.constants.DocumentType;
import com.fons.cloud.ai.rag.common.document.DocumentParseRequest;
import com.fons.cloud.ai.rag.common.document.DocumentParseResult;
import com.fons.cloud.ai.rag.common.document.DocumentSource;
import com.fons.cloud.ai.rag.common.document.DocumentSources;
import com.fons.cloud.ai.rag.common.document.ParserSelection;
import com.fons.cloud.ai.rag.langchain.document.LangChain4jDocumentParserFacade;
import dev.langchain4j.data.document.Document;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Fons4AI 文档解析适配器。
 *
 * <p>适配 Fons4AI Parser SPI 为 rag2okf 领域端口 {@link DocumentParserPort}。
 * 不伪造页码（AC-014），不静默切换 provider（AC-014）。
 *
 * @author hongqy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Fons4AiDocumentParserAdapter implements DocumentParserPort {

    private final DocumentArtifactStore artifactStore;
    private final LangChain4jDocumentParserFacade parserFacade;
    private final ObjectMapper objectMapper;

    @Override
    public ParseResult parse(ParseRequest request) {
        // 1. 从 MinIO 读取原文件
        ArtifactReference originalRef = new ArtifactReference(
                request.scope(), ArtifactType.ORIGINAL, request.documentKey(), request.originalFilename());
        ArtifactContent originalContent = artifactStore.open(originalRef);
        InputStream originalStream = originalContent.inputStream();

        try {
            // 2. 构造 DocumentSource 和 DocumentParseRequest
            DocumentSource source = DocumentSources.fromInputStream(
                    originalStream, request.originalFilename(), request.contentType(), 100L * 1024 * 1024);
            DocumentType documentType = resolveDocumentType(request.originalFilename());
            String fileExtension = extractExtension(request.originalFilename());
            ParserSelection parserSelection = resolveParserSelection(request.parserProfile());

            DocumentParseRequest parseRequest = new DocumentParseRequest(
                    source, documentType, fileExtension, parserSelection, Map.of(), Map.of());

            // 3. 委托 Fons4AI 解析器 SPI 解析
            long startTime = System.nanoTime();
            DocumentParseResult<Document> result = parserFacade.parseWithTrace(parseRequest);
            long durationNanos = System.nanoTime() - startTime;

            // 4. 规范化为 ParseManifest
            ParseManifest manifest = toParseManifest(
                    request, result, durationNanos, parserSelection);

            if (manifest.blockCount() == 0) {
                throw new DocumentArtifactException(
                        "解析产物为空: " + request.documentKey());
            }

            // 5. 写入 ParseManifest 到 MinIO
            StoredArtifact manifestArtifact = writeManifest(
                    request.scope(), ArtifactType.PARSED_MANIFEST,
                    request.parseRevisionKey(), manifest);

            // 6. 写入 SourceAnchor Manifest 到 MinIO
            StoredArtifact sourceAnchorArtifact = writeSourceAnchors(
                    request.scope(), request.parseRevisionKey(), manifest.blocks());

            log.info("Parse completed: documentKey={}, parseRevisionKey={}, blocks={}",
                    request.documentKey(), request.parseRevisionKey(), manifest.blockCount());

            return new ParseResult(manifest, manifestArtifact, sourceAnchorArtifact);

        } finally {
            try {
                originalStream.close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }

    private ParseManifest toParseManifest(
            ParseRequest request, DocumentParseResult<Document> result,
            long durationNanos, ParserSelection selection) {

        Document document = result.payload();
        String fullText = document.text();

        // 按段落分块，不伪造页码
        List<ParsedBlock> blocks = splitToBlocks(fullText, document);

        String contentHash = sha256(fullText);
        ParserTrace trace = new ParserTrace(
                result.parseTrace() != null ? result.parseTrace().provider() : "unknown",
                durationNanos / 1_000_000,
                result.parseTrace() != null ? result.parseTrace().outputFormat() : "MARKDOWN",
                selection.mode().name() + " 选型"
        );

        return new ParseManifest(
                request.parseRevisionKey(),
                request.documentKey(),
                request.parserProfile(),
                trace,
                blocks,
                blocks.size(),
                contentHash
        );
    }

    /**
     * 将文本按段落分割为块。
     *
     * <p>P0 不从 LangChain4j metadata 提取页码，统一使用 SourceAnchor.NONE。
     * 不伪造页码（AC-014）。
     */
    private List<ParsedBlock> splitToBlocks(String text, Document document) {
        List<ParsedBlock> blocks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return blocks;
        }

        String[] paragraphs = text.split("\n\n+");
        for (int i = 0; i < paragraphs.length; i++) {
            String content = paragraphs[i].trim();
            if (content.isEmpty()) {
                continue;
            }
            blocks.add(new ParsedBlock(i, content, SourceAnchor.none()));
        }
        return blocks;
    }

    private StoredArtifact writeManifest(
            DocumentArtifactStore.ArtifactScope scope, ArtifactType type,
            String revisionKey, Object manifest) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(manifest);
            try (InputStream is = new ByteArrayInputStream(json)) {
                return artifactStore.storeManifest(new ManifestArtifactRequest(
                        scope, type, revisionKey, is));
            }
        } catch (IOException e) {
            throw new DocumentArtifactException("序列化 Manifest 失败: " + revisionKey, e);
        }
    }

    private StoredArtifact writeSourceAnchors(
            DocumentArtifactStore.ArtifactScope scope, String revisionKey,
            List<ParsedBlock> blocks) {
        try {
            List<Map<String, Object>> anchorList = blocks.stream()
                    .map(b -> Map.<String, Object>of(
                            "index", b.index(),
                            "locatorType", b.sourceAnchor().locatorType(),
                            "page", b.sourceAnchor().page() != null ? b.sourceAnchor().page() : "null",
                            "blockIndex", b.sourceAnchor().blockIndex() != null
                                    ? b.sourceAnchor().blockIndex() : "null"))
                    .toList();
            byte[] json = objectMapper.writeValueAsBytes(anchorList);
            try (InputStream is = new ByteArrayInputStream(json)) {
                return artifactStore.storeManifest(new ManifestArtifactRequest(
                        scope, ArtifactType.SOURCE_ANCHOR_MANIFEST, revisionKey, is));
            }
        } catch (IOException e) {
            throw new DocumentArtifactException("序列化 SourceAnchor Manifest 失败: " + revisionKey, e);
        }
    }

    private DocumentType resolveDocumentType(String filename) {
        String ext = extractExtension(filename);
        for (DocumentType type : DocumentType.values()) {
            if (type.match(ext)) {
                return type;
            }
        }
        return DocumentType.TEXT;
    }

    private ParserSelection resolveParserSelection(String parserProfile) {
        if (parserProfile == null || parserProfile.isBlank()
                || "NATIVE_TIKA".equalsIgnoreCase(parserProfile)) {
            return ParserSelection.defaultNative();
        }
        if ("MINERU_LAYOUT".equalsIgnoreCase(parserProfile)) {
            return ParserSelection.explicit("mineru", java.util.Set.of());
        }
        // 未知 profile 使用 DEFAULT，不静默切换
        return ParserSelection.defaultNative();
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", e);
        }
    }
}
