package com.fons.cloud.ai.rag2okf.infrastructure.adapter.document;

import com.fons.cloud.ai.rag.common.constants.DocumentType;
import com.fons.cloud.ai.rag.common.document.DocumentParseRequest;
import com.fons.cloud.ai.rag.common.document.DocumentParseResult;
import com.fons.cloud.ai.rag.common.document.DocumentSource;
import com.fons.cloud.ai.rag.common.document.DocumentSources;
import com.fons.cloud.ai.rag.common.document.ParserSelection;
import com.fons.cloud.ai.rag.langchain.document.LangChain4jDocumentParserFacade;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTraceStep;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import dev.langchain4j.data.document.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in 确定性基础提取适配器，不伪造页码或时间锚点。
 *
 * @author hongqy
 */
@Component
public class BuiltInBaseExtractor {

    private static final long MAX_SOURCE_BYTES = 100L * 1024 * 1024;
    private final LangChain4jDocumentParserFacade parserFacade;

    public BuiltInBaseExtractor(LangChain4jDocumentParserFacade parserFacade) {
        this.parserFacade = parserFacade;
    }

    /**
     * 使用现有 LangChain4j Facade 提取确定性文本原料。
     *
     * <p>Facade 返回的 Document 仅在本方法内部使用。文本作为一个原始块保留，空行不会被
     * 转换为 RAG 分块边界。</p>
     *
     * @param context 受控源文件上下文
     * @param textRequired 是否必须得到原生文本
     * @return 文本原料；允许 OCR 补齐的格式可返回空块列表
     */
    public RawParseResult extractText(ParseExecutionContext context, boolean textRequired) {
        try (InputStream sourceStream = context.openSourceStream()) {
            DocumentSource source = DocumentSources.fromInputStream(
                    sourceStream, context.filename(), context.contentType(), MAX_SOURCE_BYTES);
            DocumentParseRequest request = new DocumentParseRequest(
                    source, resolveType(context.filename()), extension(context.filename()),
                    ParserSelection.defaultNative(), Map.of(), Map.of());
            DocumentParseResult<Document> result = parserFacade.parseWithTrace(request);
            String text = result.payload() == null ? null : result.payload().text();
            if (text == null || text.isBlank()) {
                if (textRequired) {
                    throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR);
                }
                return new RawParseResult(context.filename(), null, null, null,
                        List.of(), List.of(), localTrace());
            }
            return new RawParseResult(context.filename(), null, null, null, List.of(new RawParseBlock(
                    ParsedBlock.PARAGRAPH, text.strip(), null, null, null,
                    SourceAnchor.none(), Map.of())),
                    List.of(SourceAnchor.SOURCE_ANCHOR_UNAVAILABLE_WARNING), localTrace());
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR, exception);
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR, exception);
        }
    }

    private List<ParseTraceStep> localTrace() {
        return List.of(new ParseTraceStep(
                "BASE_EXTRACTION", null, null, ParseTraceStep.STATUS_SUCCESS,
                ParseTraceStep.UNKNOWN_DURATION_MILLIS, null));
    }

    private DocumentType resolveType(String filename) {
        String extension = extension(filename);
        for (DocumentType type : DocumentType.values()) {
            if (type.match(extension)) {
                return type;
            }
        }
        return DocumentType.TEXT;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
