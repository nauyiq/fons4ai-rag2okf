package com.fons.cloud.ai.rag2okf.common.dto;

import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * 上传原文件进入对象存储前的白名单、文件名、大小和文件签名校验策略。
 *
 * @author hongqy
 */
@Component
public class FileValidationPolicy {

    /** 单文件默认最大字节数：50 MiB。 */
    public static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;
    private static final int SIGNATURE_LENGTH = 8;
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "txt", "text/plain",
            "md", "text/markdown",
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final long maxFileSizeBytes;

    /**
     * 提供默认阈值，供不加载 Spring 容器的单元测试和受控工具使用。
     */
    public FileValidationPolicy() {
        this(MAX_FILE_SIZE_BYTES);
    }

    /**
     * 创建可通过应用配置覆盖大小阈值的校验策略。
     *
     * @param maxFileSizeBytes 单文件最大字节数
     */
    @Autowired
    public FileValidationPolicy(@Value("${rag2okf.document.max-file-size-bytes:52428800}") long maxFileSizeBytes) {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must be positive");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * 校验上传声明和签名，并返回继续流式上传时同步计算摘要的受控文件。
     *
     * @param filename 浏览器提交的原始文件名
     * @param contentType 浏览器声明的 MIME 类型
     * @param inputStream 文件内容流
     * @param declaredSize 浏览器声明的文件大小，未知时传 -1
     * @return 已通过前置校验的受控文件流
     */
    public ValidatedFile validate(String filename, String contentType, InputStream inputStream, long declaredSize) {
        String safeFilename = requireSafeFilename(filename);
        String extension = extensionOf(safeFilename);
        String expectedContentType = SUPPORTED_TYPES.get(extension);
        if (expectedContentType == null || !expectedContentType.equals(normalizeContentType(contentType))) {
            throw new DocumentArtifactException();
        }
        if (inputStream == null || declaredSize == 0 || declaredSize > maxFileSizeBytes) {
            throw new DocumentArtifactException();
        }
        try {
            PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, SIGNATURE_LENGTH);
            byte[] signature = pushbackInputStream.readNBytes(SIGNATURE_LENGTH);
            if (signature.length == 0) {
                throw new DocumentArtifactException();
            }
            verifySignature(extension, signature);
            pushbackInputStream.unread(signature);
            return new ValidatedFile(safeFilename, expectedContentType,
                    new DigestingLimitedInputStream(pushbackInputStream, maxFileSizeBytes));
        } catch (IOException exception) {
            throw new DocumentArtifactException(exception);
        }
    }

    private String requireSafeFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0
                || filename.chars().anyMatch(character -> character == 0 || Character.isISOControl(character))) {
            throw new DocumentArtifactException();
        }
        return filename.strip();
    }

    private String extensionOf(String filename) {
        int separator = filename.lastIndexOf('.');
        if (separator <= 0 || separator == filename.length() - 1) {
            throw new DocumentArtifactException();
        }
        return filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType).strip().toLowerCase(Locale.ROOT);
    }

    private void verifySignature(String extension, byte[] signature) {
        boolean valid = switch (extension) {
            case "pdf" -> startsWith(signature, "%PDF-".getBytes());
            case "docx" -> startsWith(signature, new byte[]{'P', 'K', 3, 4});
            case "txt", "md" -> !containsNul(signature);
            default -> false;
        };
        if (!valid) {
            throw new DocumentArtifactException();
        }
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

    private boolean containsNul(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通过读取流计算内容摘要和实际大小的受控文件。
     *
     * @author hongqy
     */
    public static final class ValidatedFile {

        private final String safeFilename;
        private final String contentType;
        private final DigestingLimitedInputStream inputStream;

        private ValidatedFile(String safeFilename, String contentType, DigestingLimitedInputStream inputStream) {
            this.safeFilename = safeFilename;
            this.contentType = contentType;
            this.inputStream = inputStream;
        }

        public String safeFilename() {
            return safeFilename;
        }

        public String contentType() {
            return contentType;
        }

        public InputStream inputStream() {
            return inputStream;
        }

        public long size() {
            return inputStream.size();
        }

        public String sha256() {
            return inputStream.sha256();
        }
    }

    private static final class DigestingLimitedInputStream extends FilterInputStream {

        private final MessageDigest messageDigest;
        private final long maxSize;
        private long size;
        private boolean completed;

        private DigestingLimitedInputStream(InputStream inputStream, long maxSize) {
            super(inputStream);
            this.maxSize = maxSize;
            try {
                this.messageDigest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0) {
                record(new byte[]{(byte) value}, 0, 1);
            } else {
                completed = true;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, length);
            if (count > 0) {
                record(buffer, offset, count);
            } else if (count < 0) {
                completed = true;
            }
            return count;
        }

        private void record(byte[] buffer, int offset, int count) {
            size += count;
            if (size > maxSize) {
                throw new DocumentArtifactException();
            }
            messageDigest.update(buffer, offset, count);
        }

        private long size() {
            return size;
        }

        private String sha256() {
            if (!completed) {
                throw new DocumentArtifactException();
            }
            try {
                MessageDigest completedDigest = (MessageDigest) messageDigest.clone();
                return HexFormat.of().formatHex(completedDigest.digest());
            } catch (CloneNotSupportedException exception) {
                throw new DocumentArtifactException(exception);
            }
        }
    }
}
