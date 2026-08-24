package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in 文件能力清单。
 *
 * <p>维护本期已启用解析器可受理的文件格式白名单，每种格式对应一个 {@link DocumentFileCapability}，
 * 包含基础提取方式、必要模型能力和增强模型能力。只有出现在本清单且扩展名与 MIME 匹配的文件才允许上传和解析。</p>
 *
 * <p>校验顺序：文件名净化→扩展名白名单→MIME 白名单。客户端 MIME 只作提示，
 * 以服务端探测的 MIME 为准。扩展名和 MIME 不匹配时视为伪装文件并返回空。</p>
 *
 * <p>扩展格式必须先增加基础提取器或明确必要模型能力，再更新本清单；
 * 不能先允许上传后在解析器里随意返回"不支持"。</p>
 *
 * @author hongqy
 */
public class BuiltInFileCapabilityCatalog {

    /** 扩展名到文件能力的映射，键为小写扩展名。 */
    private static final Map<String, DocumentFileCapability> CAPABILITY_BY_EXTENSION = Map.ofEntries(
        Map.entry("txt", new DocumentFileCapability(
            "纯文本", "txt", List.of("text/plain"),
            "UTF-8/受控编码文本", List.of(), List.of("LLM"))),
        Map.entry("md", new DocumentFileCapability(
            "Markdown", "md", List.of("text/markdown", "text/x-markdown"),
            "标题、列表、代码、表格", List.of(), List.of("LLM"))),
        Map.entry("markdown", new DocumentFileCapability(
            "Markdown", "markdown", List.of("text/markdown", "text/x-markdown"),
            "标题、列表、代码、表格", List.of(), List.of("LLM"))),
        Map.entry("pdf", new DocumentFileCapability(
            "PDF", "pdf", List.of("application/pdf"),
            "文本、页、基础布局", List.of("OCR"), List.of("VLM", "LLM"))),
        Map.entry("docx", new DocumentFileCapability(
            "Word", "docx", List.of(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "标题、段落、表格、图片引用", List.of("OCR"), List.of("VLM", "LLM"))),
        Map.entry("png", new DocumentFileCapability(
            "图片", "png", List.of("image/png"),
            "媒体元数据和原图锚点", List.of("OCR"), List.of("VLM", "LLM"))),
        Map.entry("jpg", new DocumentFileCapability(
            "图片", "jpg", List.of("image/jpeg"),
            "媒体元数据和原图锚点", List.of("OCR"), List.of("VLM", "LLM"))),
        Map.entry("jpeg", new DocumentFileCapability(
            "图片", "jpeg", List.of("image/jpeg"),
            "媒体元数据和原图锚点", List.of("OCR"), List.of("VLM", "LLM"))),
        Map.entry("webp", new DocumentFileCapability(
            "图片", "webp", List.of("image/webp"),
            "媒体元数据和原图锚点", List.of("OCR"), List.of("VLM", "LLM"))),
        Map.entry("wav", new DocumentFileCapability(
            "音频", "wav", List.of("audio/wav", "audio/x-wav"),
            "容器元数据", List.of("ASR"), List.of("LLM"))),
        Map.entry("mp3", new DocumentFileCapability(
            "音频", "mp3", List.of("audio/mpeg"),
            "容器元数据", List.of("ASR"), List.of("LLM"))),
        Map.entry("m4a", new DocumentFileCapability(
            "音频", "m4a", List.of("audio/mp4", "audio/x-m4a"),
            "容器元数据", List.of("ASR"), List.of("LLM")))
    );

    /**
     * 仅按文件名扩展名查找文件能力。
     *
     * <p>用于上传预检在魔数验证前判断扩展名是否已登记：扩展名未在白名单时
     * 直接以“文件类型尚不支持”拒绝，不进入后续内容校验。</p>
     *
     * @param filename 文件名，用于提取扩展名，大小写不敏感
     * @return 文件能力，扩展名未登记时返回空
     */
    public Optional<DocumentFileCapability> findByFilename(String filename) {
        String extension = extractExtension(filename);
        if (StringUtils.isBlank(extension)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CAPABILITY_BY_EXTENSION.get(extension));
    }

    /**
     * 查找文件能力。
     *
     * <p>校验顺序：扩展名白名单→MIME 白名单。扩展名或 MIME 不匹配时返回空，
     * 调用方应将其视为不支持或伪装文件。</p>
     *
     * @param filename 文件名，用于提取扩展名，大小写不敏感
     * @param detectedMimeType 服务端探测的 MIME 类型，大小写不敏感；为 null 时视为不匹配
     * @return 文件能力，如果扩展名未登记或 MIME 不匹配则返回空
     */
    public Optional<DocumentFileCapability> find(String filename, String detectedMimeType) {
        String extension = extractExtension(filename);
        if (StringUtils.isBlank(extension)) {
            return Optional.empty();
        }
        DocumentFileCapability capability = CAPABILITY_BY_EXTENSION.get(extension);
        if (capability == null) {
            return Optional.empty();
        }
        if (StringUtils.isBlank(detectedMimeType) || !capability.mimeWhitelist().contains(detectedMimeType.toLowerCase())) {
            return Optional.empty();
        }
        return Optional.of(capability);
    }

    /**
     * 查找文件能力，未找到时抛出 {@link DocumentProcessingException}。
     *
     * @param filename 文件名
     * @param detectedMimeType 服务端探测的 MIME 类型
     * @return 文件能力
     * @throws DocumentProcessingException 如果扩展名未登记或 MIME 不匹配
     */
    public DocumentFileCapability require(String filename, String detectedMimeType) {
        return find(filename, detectedMimeType)
            .orElseThrow(() -> new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_UNSUPPORTED_FILE_TYPE));
    }

    /**
     * 从文件名提取小写扩展名。
     *
     * <p>返回不含点的扩展名（如 {@code "txt"}）。无扩展名、空文件名或以点结尾时返回 null。</p>
     *
     * @param filename 文件名，可能为 null 或空
     * @return 小写扩展名，或 null
     */
    private static String extractExtension(String filename) {
        if (StringUtils.isBlank(filename)) {
            return null;
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return null;
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }
}
