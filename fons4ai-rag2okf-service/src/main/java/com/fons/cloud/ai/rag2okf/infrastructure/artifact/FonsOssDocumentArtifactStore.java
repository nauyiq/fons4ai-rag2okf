package com.fons.cloud.ai.rag2okf.infrastructure.artifact;

import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
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
 * 通过 Fons4Cloud OSS 服务保存私有文档产物的基础设施适配器。
 *
 * @author hongqy
 */
@Component
public class FonsOssDocumentArtifactStore implements DocumentArtifactStore {

    private static final Pattern BUSINESS_KEY = Pattern.compile("[0-9A-HJKMNP-TV-Z]{26}");
    private static final String CONTENT_TYPE_METADATA = "content-type";
    private static final String ORIGINAL_FILENAME_METADATA = "original-filename";
    private static final String MANIFEST_CONTENT_TYPE = "application/json";

    private final OssStoreService ossStoreService;

    public FonsOssDocumentArtifactStore(OssStoreService ossStoreService) {
        this.ossStoreService = ossStoreService;
    }

    @Override
    public StoredArtifact storeOriginal(OriginalArtifactRequest request) {
        requireOriginalRequest(request);
        String objectKey = originalObjectKey(request.scope(), request.originalFilename());
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(ORIGINAL_FILENAME_METADATA, request.originalFilename());
        metadata.put(CONTENT_TYPE_METADATA, request.contentType());
        return upload(objectKey, request.originalFilename(), request.inputStream(), metadata);
    }

    @Override
    public StoredArtifact storeManifest(ManifestArtifactRequest request) {
        requireManifestRequest(request);
        String objectKey = manifestObjectKey(request.scope(), request.type(), request.revisionKey());
        return upload(objectKey, request.type().name().toLowerCase() + ".json", request.inputStream(),
                Map.of(CONTENT_TYPE_METADATA, MANIFEST_CONTENT_TYPE));
    }

    @Override
    public ArtifactContent open(ArtifactReference reference) {
        String objectKey = objectKey(reference);
        try {
            OssObjectResponse response = ossStoreService.download(OssObjectRequest.builder().objectKey(objectKey).build());
            if (response == null || response.getInputStream() == null) {
                throw new DocumentArtifactException();
            }
            return new ArtifactContent(response.getInputStream());
        } catch (DocumentArtifactException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DocumentArtifactException(exception);
        }
    }

    @Override
    public boolean exists(ArtifactReference reference) {
        try {
            return ossStoreService.exists(OssObjectRequest.builder().objectKey(objectKey(reference)).build());
        } catch (RuntimeException exception) {
            throw new DocumentArtifactException(exception);
        }
    }

    @Override
    public void delete(ArtifactReference reference) {
        deleteObject(objectKey(reference));
    }

    private StoredArtifact upload(String objectKey, String filename, InputStream inputStream, Map<String, String> metadata) {
        MessageDigest digest = sha256Digest();
        CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        try (DigestInputStream digestInputStream = new DigestInputStream(countingInputStream, digest)) {
            ossStoreService.upload(OssUploadRequest.builder()
                    .objectKey(objectKey)
                    .filename(filename)
                    .inputStream(digestInputStream)
                    .metadata(metadata)
                    .build());
            return new StoredArtifact(objectKey, HexFormat.of().formatHex(digest.digest()), countingInputStream.count(), metadata);
        } catch (RuntimeException exception) {
            compensateFailedUpload(objectKey, exception);
            throw new DocumentArtifactException(exception);
        } catch (Exception exception) {
            compensateFailedUpload(objectKey, exception);
            throw new DocumentArtifactException(exception);
        }
    }

    private void compensateFailedUpload(String objectKey, Exception originalFailure) {
        try {
            ossStoreService.delete(OssObjectRequest.builder().objectKey(objectKey).build());
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private void deleteObject(String objectKey) {
        try {
            ossStoreService.delete(OssObjectRequest.builder().objectKey(objectKey).build());
        } catch (RuntimeException exception) {
            throw new DocumentArtifactException(exception);
        }
    }

    private String objectKey(ArtifactReference reference) {
        if (reference == null || reference.type() == null) {
            throw new DocumentArtifactException();
        }
        return reference.type() == ArtifactType.ORIGINAL
                ? originalObjectKey(reference.scope(), reference.detail())
                : manifestObjectKey(reference.scope(), reference.type(), reference.revisionKey());
    }

    /**
     * 构造原文件对象 key。CR-014 后去掉 versions/{versionKey} 段，
     * 直接使用 scope.documentKey 定位文档级原文件。
     */
    private String originalObjectKey(ArtifactScope scope, String originalFilename) {
        validateScope(scope);
        String safeName = (originalFilename != null && !originalFilename.isBlank())
                ? sanitizeFilename(originalFilename)
                : "original";
        return "workspaces/%s/knowledge-bases/%s/documents/%s/%s".formatted(
                scope.workspaceKey(), scope.knowledgeBaseKey(), scope.documentKey(), safeName
        );
    }

    private String manifestObjectKey(ArtifactScope scope, ArtifactType type, String revisionKey) {
        validateScope(scope);
        validateBusinessKey(revisionKey);
        return switch (type) {
            case PARSED_MANIFEST -> baseDocumentPath(scope) + "parses/" + revisionKey + "/parsed-manifest.json";
            case SOURCE_ANCHOR_MANIFEST -> baseDocumentPath(scope) + "parses/" + revisionKey + "/source-anchor-manifest.json";
            case CHUNK_MANIFEST -> baseDocumentPath(scope) + "chunks/" + revisionKey + "/chunk-manifest.json";
            case PUBLICATION_MANIFEST -> baseDocumentPath(scope) + "publications/" + revisionKey + "/publication-manifest.json";
            case ORIGINAL -> throw new DocumentArtifactException();
        };
    }

    private String baseDocumentPath(ArtifactScope scope) {
        return "workspaces/%s/knowledge-bases/%s/documents/%s/".formatted(
                scope.workspaceKey(), scope.knowledgeBaseKey(), scope.documentKey()
        );
    }

    private void requireOriginalRequest(OriginalArtifactRequest request) {
        if (request == null || request.inputStream() == null || !isSafeMetadataFilename(request.originalFilename())
                || request.contentType() == null || request.contentType().isBlank()) {
            throw new DocumentArtifactException();
        }
    }

    private void requireManifestRequest(ManifestArtifactRequest request) {
        if (request == null || request.inputStream() == null || request.type() == null
                || request.type() == ArtifactType.ORIGINAL) {
            throw new DocumentArtifactException();
        }
    }

    private boolean isSafeMetadataFilename(String filename) {
        return filename != null && !filename.isBlank() && filename.length() <= 255
                && filename.indexOf('/') < 0 && filename.indexOf('\\') < 0
                && filename.chars().noneMatch(character -> character == 0 || Character.isISOControl(character));
    }

    private String sanitizeFilename(String filename) {
        String name = filename.strip();
        name = name.replace('/', '_').replace('\\', '_');
        name = name.replaceAll("[\\x00-\\x1f]", "_");
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        return name.isBlank() ? "original" : name;
    }

    private void validateScope(ArtifactScope scope) {
        if (scope == null) {
            throw new DocumentArtifactException();
        }
        validateBusinessKey(scope.workspaceKey());
        validateBusinessKey(scope.knowledgeBaseKey());
        validateBusinessKey(scope.documentKey());
    }

    private void validateBusinessKey(String value) {
        if (value == null || !BUSINESS_KEY.matcher(value).matches()) {
            throw new DocumentArtifactException();
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new DocumentArtifactException(exception);
        }
    }

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
