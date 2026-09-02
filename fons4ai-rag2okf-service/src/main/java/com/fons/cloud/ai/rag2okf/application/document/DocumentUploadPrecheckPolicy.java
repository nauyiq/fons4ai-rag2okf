package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentPrecheckedFile;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.BuiltInFileCapabilityCatalog;
import com.fons.cloud.common.result.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 上传文件预检策略。
 *
 * <p>按设计 §4.2 校验顺序执行进入对象存储前的预检：文件名净化→扩展名白名单
 * （复用 {@link BuiltInFileCapabilityCatalog}，不复制规则）→大小限制→魔数校验。
 * 客户端声明的 MIME 只作提示，服务端以魔数校验结果为准判定内容与扩展名一致。</p>
 *
 * <p>阈值通过 {@code rag2okf.document.*} 配置注入（设计 §4.2 初始安全默认值：
 * 单文件 100 MiB、单批 50 文件且总计 500 MiB），不写死在调用方。</p>
 *
 * <p>预检失败以 {@link R#failed} 返回稳定错误码，不抛异常；应用服务直接透传失败结果。</p>
 *
 * @author hongqy
 */
@Component
public class DocumentUploadPrecheckPolicy {

    /** 单文件默认最大字节数：100 MiB（设计 §4.2 初始安全默认值）。 */
    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 100L * 1024L * 1024L;

    /** 单批默认最大文件数（设计 §4.2 初始安全默认值）。 */
    public static final int DEFAULT_BATCH_MAX_FILES = 50;

    /** 单批默认最大总字节数：500 MiB（设计 §4.2 初始安全默认值）。 */
    public static final long DEFAULT_BATCH_MAX_TOTAL_SIZE_BYTES = 500L * 1024L * 1024L;

    /** 魔数最大读取长度：覆盖 RIFF 头（12 字节）与 MP4 ftyp 盒（8 字节）判定。 */
    private static final int MAGIC_LENGTH = 12;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DOCX_MAGIC = {'P', 'K', 3, 4};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final BuiltInFileCapabilityCatalog capabilityCatalog;
    private final long maxFileSizeBytes;
    private final int batchMaxFiles;
    private final long batchMaxTotalSizeBytes;

    /**
     * 创建预检策略。
     *
     * @param capabilityCatalog        Built-in 文件能力清单，扩展名白名单的唯一事实源
     * @param maxFileSizeBytes         单文件最大字节数
     * @param batchMaxFiles            单批最大文件数
     * @param batchMaxTotalSizeBytes   单批最大总字节数
     */
    public DocumentUploadPrecheckPolicy(
            BuiltInFileCapabilityCatalog capabilityCatalog,
            @Value("${rag2okf.document.upload.max-file-size-bytes:" + DEFAULT_MAX_FILE_SIZE_BYTES + "}")
            long maxFileSizeBytes,
            @Value("${rag2okf.document.upload.batch-max-files:" + DEFAULT_BATCH_MAX_FILES + "}")
            int batchMaxFiles,
            @Value("${rag2okf.document.upload.batch-max-total-size-bytes:" + DEFAULT_BATCH_MAX_TOTAL_SIZE_BYTES + "}")
            long batchMaxTotalSizeBytes) {
        this.capabilityCatalog = capabilityCatalog;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.batchMaxFiles = batchMaxFiles;
        this.batchMaxTotalSizeBytes = batchMaxTotalSizeBytes;
    }

    /**
     * 单文件预检：文件名、扩展名白名单、大小与魔数。
     *
     * <p>魔数校验通过后把已读字节推回流首，返回的输入流仍包含完整文件内容，
     * 供后续流式写入对象存储。</p>
     *
     * @param filename     浏览器提交的原始文件名
     * @param inputStream  文件内容流
     * @param declaredSize 声明的文件字节数
     * @return 通过预检的受控文件，或携带稳定错误码的失败结果
     */
    public R<DocumentPrecheckedFile> precheck(String filename, InputStream inputStream, long declaredSize) {
        String safeFilename = safeFilename(filename);
        if (safeFilename == null) {
            return R.failed(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        DocumentFileCapability capability = capabilityCatalog.findByFilename(safeFilename)
                .orElse(null);
        if (capability == null) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_UNSUPPORTED_FILE_TYPE);
        }
        if (declaredSize <= 0) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_FILE_SECURITY_REJECTED);
        }
        if (declaredSize > maxFileSizeBytes) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_FILE_LIMIT_EXCEEDED);
        }
        String extension = extensionOf(safeFilename);
        if (extension == null) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_UNSUPPORTED_FILE_TYPE);
        }
        try {
            PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, MAGIC_LENGTH);
            byte[] magic = pushbackInputStream.readNBytes(MAGIC_LENGTH);
            if (magic.length == 0) {
                return R.failed(Rag2OkfResultCode.DOCUMENT_FILE_SECURITY_REJECTED);
            }
            if (!matchesMagic(extension, magic)) {
                return R.failed(Rag2OkfResultCode.DOCUMENT_FILE_SECURITY_REJECTED);
            }
            pushbackInputStream.unread(magic);
            return R.ok(new DocumentPrecheckedFile(
                    safeFilename, capability.mimeWhitelist().get(0), pushbackInputStream));
        } catch (IOException exception) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_FILE_SECURITY_REJECTED);
        }
    }

    /**
     * 批量上传整批预检：文件数量与总大小。
     *
     * @param files 批量上传文件列表
     * @return 成功结果，或携带稳定错误码的失败结果
     */
    public R<Void> precheckBatch(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return R.failed(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        if (files.size() > batchMaxFiles) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_FILE_LIMIT_EXCEEDED);
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > batchMaxTotalSizeBytes) {
            return R.failed(Rag2OkfResultCode.DOCUMENT_FILE_LIMIT_EXCEEDED);
        }
        return R.ok(null);
    }

    /**
     * 校验并净化文件名：非空、不超长、不含路径分隔符与控制字符。
     *
     * @param filename 原始文件名
     * @return 净化后的安全文件名，不合法时返回 {@code null}
     */
    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0
                || filename.chars().anyMatch(character -> character == 0 || Character.isISOControl(character))) {
            return null;
        }
        return filename.strip();
    }

    /**
     * 按扩展名校验魔数，内容与扩展名不符视为伪装文件。
     *
     * @param extension 文件扩展名
     * @param magic     已读取的魔数
     * @return 魔数与扩展名匹配返回 {@code true}
     */
    private boolean matchesMagic(String extension, byte[] magic) {
        return switch (extension) {
            case "txt", "md", "markdown" -> !containsNul(magic);
            case "pdf" -> startsWith(magic, PDF_MAGIC);
            case "docx" -> startsWith(magic, DOCX_MAGIC);
            case "png" -> startsWith(magic, PNG_MAGIC);
            case "jpg", "jpeg" -> startsWith(magic, JPEG_MAGIC);
            case "webp" -> isRiffContainer(magic, "WEBP");
            case "wav" -> isRiffContainer(magic, "WAVE");
            case "mp3" -> isMp3(magic);
            case "m4a" -> isMp4Container(magic);
            default -> false;
        };
    }

    private boolean startsWith(byte[] source, byte[] expected) {
        if (source.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (source[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    /** RIFF 容器格式（webp/wav）：前 4 字节 RIFF 且 8～11 字节为格式标识。 */
    private boolean isRiffContainer(byte[] magic, String format) {
        return magic.length >= 12
                && startsWith(magic, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && new String(magic, 8, 4, StandardCharsets.US_ASCII).equals(format);
    }

    /** MP3：ID3v2 标签头或 MPEG 音频帧同步字节。 */
    private boolean isMp3(byte[] magic) {
        if (startsWith(magic, "ID3".getBytes(StandardCharsets.US_ASCII))) {
            return true;
        }
        return magic.length >= 2 && (magic[0] & 0xFF) == 0xFF && (magic[1] & 0xE0) == 0xE0;
    }

    /** MP4/M4A 容器：偏移 4～7 字节为 ftyp 品牌。 */
    private boolean isMp4Container(byte[] magic) {
        return magic.length >= 8
                && "ftyp".equals(new String(magic, 4, 4, StandardCharsets.US_ASCII));
    }

    private boolean containsNul(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private String extensionOf(String filename) {
        int separator = filename.lastIndexOf('.');
        if (separator <= 0 || separator == filename.length() - 1) {
            return null;
        }
        return filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

}
