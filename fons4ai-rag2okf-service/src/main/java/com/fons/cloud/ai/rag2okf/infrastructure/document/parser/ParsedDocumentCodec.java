package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * ParsedDocument 的稳定 JSON 与可选 Markdown 编解码器。
 *
 * @author hongqy
 */
@Component
public class ParsedDocumentCodec {

    /**
     * 将已校验的解析制品编码为 JSON 字节。
     *
     * @param document 已校验的解析制品
     * @return 用于对象存储的 JSON 字节
     * @throws DocumentProcessingException 编码失败时抛出安全的解析制品错误
     */
    public byte[] writeJson(ParsedDocument document) {
        try {
            return JSON.toJSONBytes(document);
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_ARTIFACT_ERROR, exception);
        }
    }

    /**
     * 从对象存储读取的 JSON 字节恢复解析制品。
     *
     * @param content 已保存的 JSON 字节
     * @return 反序列化后的解析制品
     * @throws DocumentProcessingException 解码失败时抛出安全的解析制品错误
     */
    public ParsedDocument readJson(byte[] content) {
        try {
            return JSON.parseObject(content, ParsedDocument.class);
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_ARTIFACT_ERROR, exception);
        }
    }

    /**
     * 将文本型解析块投影为供预览使用的 Markdown。
     *
     * <p>该投影不承担 RAG 分块职责，也不下载或改写媒体 URL。</p>
     *
     * @param document 已校验的解析制品
     * @return UTF-8 编码的 Markdown 字节
     */
    public byte[] writeMarkdown(ParsedDocument document) {
        StringBuilder markdown = new StringBuilder();
        for (ParsedBlock block : document.blocks()) {
            if (block.text() == null || block.text().isBlank()) {
                continue;
            }
            if (ParsedBlock.HEADING.equals(block.type())) {
                markdown.append("#".repeat(block.level())).append(' ');
            } else if (ParsedBlock.LIST_ITEM.equals(block.type())) {
                markdown.append("- ");
            }
            markdown.append(block.text()).append("\n\n");
        }
        return markdown.toString().getBytes(StandardCharsets.UTF_8);
    }
}
