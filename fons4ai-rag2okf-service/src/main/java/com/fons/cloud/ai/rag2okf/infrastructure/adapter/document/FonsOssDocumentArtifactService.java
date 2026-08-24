package com.fons.cloud.ai.rag2okf.infrastructure.adapter.document;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.file.api.OssStoreService;
import com.fons.cloud.file.common.request.OssObjectRequest;
import com.fons.cloud.file.common.request.OssUploadRequest;
import com.fons.cloud.file.common.response.OssObjectResponse;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 文档 source 制品的 MinIO 存储服务。
 *
 * <p>对象键按设计 §5.3 结构构造：
 * {@code workspaces/{workspaceKey}/knowledge-bases/{kbKey}/documents/{documentKey}/
 * sources/{fileToken}/{sanitizedFilename}}。所有路径段由系统业务 key 构造，
 * 文件名只出现在末段且经净化；同一文档同名文件因 fileToken 不同天然隔离，
 * 不会共享 objectKey。</p>
 *
 * <p>写入为流式：上传过程中同时统计字节数并计算 SHA-256，不在内存缓存全文。
 * 底层上传失败时以显式补偿删除已生成的对象键，避免残留对象；异常仅携带
 * 安全错误码与内部原因，不向调用方透出 objectKey、bucket 或访问 URL。</p>
 *
 * <p>本服务只提供源制品的写入、读取与存在校验；按文档清理等删除能力
 * 归删除清理任务链路（TP-006），不在本类提前实现。</p>
 *
 * @author hongqy
 */
@Component
public class FonsOssDocumentArtifactService {

    /**
     * 26 位 ULID 兼容业务 key（与 {@code BusinessKeyGenerator} 字符集一致，
     * 排除 I、L、O、U），用于校验 scope 各段与 fileToken。
     */
    private static final Pattern BUSINESS_KEY = Pattern.compile("[0-9A-HJKMNP-TV-Z]{26}");

    /** 源对象键受控命名空间前缀，读取与存在校验只接受该前缀内的键。 */
    private static final String OBJECT_KEY_PREFIX = "workspaces/";

    /** 对象键长度上限，对应 kb_document_result.source_object_key VARCHAR(512)。 */
    private static final int OBJECT_KEY_MAX_LENGTH = 512;

    /** 对象键中不允许出现的路径逃逸段。 */
    private static final String PATH_ESCAPE_SEGMENT = "..";

    private static final String CONTENT_TYPE_METADATA = "content-type";
    private static final String ORIGINAL_FILENAME_METADATA = "original-filename";

    private final OssStoreService ossStoreService;

    public FonsOssDocumentArtifactService(OssStoreService ossStoreService) {
        this.ossStoreService = ossStoreService;
    }

    /**
     * 流式写入文档 source 制品并返回受控描述。
     *
     * <p>objectKey 由系统业务 key 与净化文件名构造，调用方不能直接指定；
     * 返回的 sha256 与 sizeBytes 由服务端流式计算，供结果表登记与校验。</p>
     *
     * @param command 源文件写入命令
     * @return 已写入对象的对象键、SHA-256 摘要与字节数
     * @throws DocumentProcessingException 命令参数非法或底层写入失败（含补偿删除）时抛出
     */
    public StoredSourceArtifact storeSource(SourceArtifactCommand command) {
        requireCommand(command);
        String sanitizedFilename = sanitizeFilename(command.originalFilename());
        String objectKey = sourceObjectKey(command, sanitizedFilename);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(ORIGINAL_FILENAME_METADATA, command.originalFilename());
        metadata.put(CONTENT_TYPE_METADATA, command.contentType());
        return upload(objectKey, sanitizedFilename, command.inputStream(), metadata);
    }

    /**
     * 打开已登记的源制品读取流；调用方负责关闭。
     *
     * <p>objectKey 只接受本服务生成的受控命名空间内的键，拒绝路径逃逸；
     * 返回内容只含读取流，不透出 bucket、endpoint 或访问 URL。</p>
     *
     * @param objectKey 结果表登记的源文件对象键
     * @return 源制品读取内容
     * @throws DocumentProcessingException objectKey 非法或底层读取失败时抛出
     */
    public SourceArtifactContent openSource(String objectKey) {
        validateObjectKey(objectKey);
        try {
            OssObjectResponse response = ossStoreService.download(
                    OssObjectRequest.builder().objectKey(objectKey).build());
            if (response == null || response.getInputStream() == null) {
                throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR);
            }
            return new SourceArtifactContent(objectKey, response.getInputStream());
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR, exception);
        }
    }

    /**
     * 判断已登记的源制品是否存在。
     *
     * @param objectKey 结果表登记的源文件对象键
     * @return true 表示对象存在
     * @throws DocumentProcessingException objectKey 非法或底层校验失败时抛出
     */
    public boolean exists(String objectKey) {
        validateObjectKey(objectKey);
        try {
            return ossStoreService.exists(OssObjectRequest.builder().objectKey(objectKey).build());
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR, exception);
        }
    }

    /**
     * 删除单个已登记的源制品对象。
     *
     * <p>仅用于上传链路的失败补偿（MinIO 写入成功但 MySQL 事务失败时回滚
     * 本次已写对象）；按文档批量清理全部制品的能力归删除清理任务链路
     * （TP-006），不在本方法范围。对象不存在视为删除成功，保证补偿幂等。</p>
     *
     * @param objectKey 本服务生成的源文件对象键
     * @throws DocumentProcessingException objectKey 非法或底层删除失败时抛出
     */
    public void deleteSource(String objectKey) {
        validateObjectKey(objectKey);
        try {
            ossStoreService.delete(OssObjectRequest.builder().objectKey(objectKey).build());
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR, exception);
        }
    }

    /**
     * 源文件写入命令；scope 各段与 fileToken 均为系统业务 key，
     * 文件内容以流传递，不得在日志或异常中输出正文。
     *
     * @param workspaceKey     工作空间业务 key
     * @param knowledgeBaseKey 知识库业务 key
     * @param documentKey      文档业务 key
     * @param fileToken        本次上传的源文件 CAS 令牌，用于对象键隔离
     * @param originalFilename 上传时原始文件名，净化后作为对象键末段
     * @param contentType      服务端校验后的 MIME 类型
     * @param inputStream      源文件读取流
     */
    public record SourceArtifactCommand(
            String workspaceKey,
            String knowledgeBaseKey,
            String documentKey,
            String fileToken,
            String originalFilename,
            String contentType,
            InputStream inputStream
    ) {
    }

    /**
     * 已写入源制品的受控描述；objectKey 登记到 {@code kb_document_result.source_object_key}。
     *
     * @param objectKey 系统生成的内部对象键
     * @param sha256    流式计算的 SHA-256 十六进制摘要
     * @param sizeBytes 实际写入字节数
     */
    public record StoredSourceArtifact(String objectKey, String sha256, long sizeBytes) {
    }

    /**
     * 源制品读取结果；调用方必须关闭流，内容不携带任何存储访问细节。
     *
     * @param objectKey   已登记的源文件对象键
     * @param inputStream 读取流
     */
    public record SourceArtifactContent(String objectKey, InputStream inputStream) {
    }

    private StoredSourceArtifact upload(String objectKey, String sanitizedFilename, InputStream inputStream,
                                        Map<String, String> metadata) {
        MessageDigest digest = sha256Digest();
        CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        try (DigestInputStream digestInputStream = new DigestInputStream(countingInputStream, digest)) {
            ossStoreService.upload(OssUploadRequest.builder()
                    .objectKey(objectKey)
                    .filename(sanitizedFilename)
                    .inputStream(digestInputStream)
                    .metadata(metadata)
                    .build());
            return new StoredSourceArtifact(objectKey, HexFormat.of().formatHex(digest.digest()),
                    countingInputStream.count());
        } catch (RuntimeException exception) {
            compensateFailedUpload(objectKey, exception);
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR, exception);
        } catch (Exception exception) {
            compensateFailedUpload(objectKey, exception);
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR, exception);
        }
    }

    /**
     * 底层写入失败后的显式补偿：删除本次已生成的对象键，避免残留对象。
     * 补偿自身的失败不掩盖原始异常，仅作为 suppressed 附加。
     */
    private void compensateFailedUpload(String objectKey, Exception originalFailure) {
        try {
            ossStoreService.delete(OssObjectRequest.builder().objectKey(objectKey).build());
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * 构造 source 对象键，结构与设计 §5.3 一致；
     * fileToken 隔离保证同文档同名重复上传不共享键。
     */
    private String sourceObjectKey(SourceArtifactCommand command, String sanitizedFilename) {
        return "workspaces/%s/knowledge-bases/%s/documents/%s/sources/%s/%s".formatted(
                command.workspaceKey(), command.knowledgeBaseKey(), command.documentKey(),
                command.fileToken(), sanitizedFilename);
    }

    /**
     * 净化文件名为对象键末段，规则集中于此：
     * 去除首尾空白、路径分隔符与控制字符替换为下划线（消除路径段与不可见字符）、
     * 超长截断到 255、净化后为空时回退固定名。
     */
    private String sanitizeFilename(String filename) {
        String name = filename.strip();
        name = name.replace('/', '_').replace('\\', '_');
        name = name.replaceAll("[\\x00-\\x1f]", "_");
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        return name.isBlank() ? "original" : name;
    }

    private void requireCommand(SourceArtifactCommand command) {
        if (command == null || command.inputStream() == null
                || command.originalFilename() == null || command.originalFilename().isBlank()
                || command.contentType() == null || command.contentType().isBlank()) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        validateBusinessKey(command.workspaceKey());
        validateBusinessKey(command.knowledgeBaseKey());
        validateBusinessKey(command.documentKey());
        validateBusinessKey(command.fileToken());
    }

    /**
     * 校验读取/存在校验传入的对象键：必须位于受控命名空间前缀内，
     * 不含路径逃逸段、反斜杠与控制字符，且不超过登记列长度。
     */
    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()
                || !objectKey.startsWith(OBJECT_KEY_PREFIX)
                || objectKey.length() > OBJECT_KEY_MAX_LENGTH
                || objectKey.indexOf('\\') >= 0
                || objectKey.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        for (String segment : objectKey.split("/")) {
            if (segment.isBlank() || PATH_ESCAPE_SEGMENT.equals(segment)) {
                throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
            }
        }
    }

    private void validateBusinessKey(String value) {
        if (value == null || !BUSINESS_KEY.matcher(value).matches()) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.DOCUMENT_SOURCE_ARTIFACT_ERROR, exception);
        }
    }

    /**
     * 流式字节数统计；与摘要流串联使用，写入的同时完成 size 统计。
     */
    private static final class CountingInputStream extends InputStream {

        private final InputStream delegate;
        private long count;

        private CountingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws java.io.IOException {
            int value = delegate.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
            int read = delegate.read(bytes, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        @Override
        public void close() throws java.io.IOException {
            delegate.close();
        }

        private long count() {
            return count;
        }
    }
}
