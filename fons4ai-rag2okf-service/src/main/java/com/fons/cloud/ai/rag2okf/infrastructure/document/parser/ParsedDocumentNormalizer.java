package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocumentMetadata;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocumentSource;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedParserInfo;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseTrace;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseBlock;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 将解析器内部结果规范化为不可变 ParsedDocument v1。
 *
 * @author hongqy
 */
@Component
public class ParsedDocumentNormalizer {

    public ParsedDocument normalize(ParseExecutionContext context, RawParseResult raw) {
        List<ParsedBlock> blocks = new ArrayList<>(raw.blocks().size());
        for (int index = 0; index < raw.blocks().size(); index++) {
            RawParseBlock block = raw.blocks().get(index);
            blocks.add(new ParsedBlock(
                    stableBlockId(context.resultVersion(), index, block),
                    index, block.type(), null, block.text(), block.level(),
                    block.table(), block.media(), block.anchor(), block.attributes(),
                    block.markdownImages(), block.outputImages()));
        }
        return new ParsedDocument(
                context.documentKey(),
                context.resultVersion(),
                new ParsedDocumentSource(
                        context.sourceFileToken(), context.filename(),
                        context.contentType(), context.sha256()),
                new ParsedParserInfo(context.parserType().getValue(), "builtin"),
                new ParsedDocumentMetadata(
                        raw.title(), raw.language(), raw.pageCount(), raw.durationMs()),
                blocks,
                raw.warnings(),
                new ParseTrace(raw.trace()));
    }

    private String stableBlockId(
            int resultVersion, int ordinal, RawParseBlock block) {
        String canonical = resultVersion + "|" + ordinal + "|" + block.type() + "|"
                + block.text() + "|" + block.anchor();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "blk-" + resultVersion + "-" + ordinal + "-"
                    + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", exception);
        }
    }
}
