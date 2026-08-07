package com.fons.cloud.ai.rag2okf.infrastructure.artifact;

import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
import com.fons.cloud.file.api.OssStoreService;
import com.fons.cloud.file.common.request.OssObjectRequest;
import com.fons.cloud.file.common.request.OssUploadRequest;
import com.fons.cloud.file.common.response.OssObjectResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 基于 Fons4Cloud OSS 契约的文档产物适配器测试。
 *
 * @author hongqy
 */
class FonsOssDocumentArtifactStoreTest {

    @Test
    void shouldStoreReadAndDeleteOriginalUsingOnlySystemGeneratedObjectKey() throws Exception {
        RecordingOssStoreService ossStoreService = new RecordingOssStoreService();
        FonsOssDocumentArtifactStore store = new FonsOssDocumentArtifactStore(ossStoreService);
        DocumentArtifactStore.ArtifactScope scope = new DocumentArtifactStore.ArtifactScope(
                "01J00000000000000000000000",
                "01J00000000000000000000001",
                "01J00000000000000000000002"
        );

        DocumentArtifactStore.StoredArtifact stored = store.storeOriginal(
                new DocumentArtifactStore.OriginalArtifactRequest(
                        scope,
                        "loan-policy.pdf",
                        "application/pdf",
                        new ByteArrayInputStream("original content".getBytes(StandardCharsets.UTF_8))
                )
        );

        assertThat(stored.objectKey()).isEqualTo(
                "workspaces/01J00000000000000000000000/knowledge-bases/01J00000000000000000000001/"
                        + "documents/01J00000000000000000000002/loan-policy.pdf"
        );
        assertThat(stored.sha256()).isEqualTo("bf573149b23303cac63c2a359b53760d919770c5d070047e76de42e2184f1046");
        assertThat(stored.metadata()).containsEntry("original-filename", "loan-policy.pdf")
                .containsEntry("content-type", "application/pdf");
        assertThat(ossStoreService.accessUrlCalls).isZero();

        try (InputStream inputStream = store.open(new DocumentArtifactStore.ArtifactReference(
                scope,
                DocumentArtifactStore.ArtifactType.ORIGINAL,
                "01J00000000000000000000003",
                "loan-policy.pdf"
        )).inputStream()) {
            assertThat(inputStream.readAllBytes()).isEqualTo("original content".getBytes(StandardCharsets.UTF_8));
        }

        store.delete(new DocumentArtifactStore.ArtifactReference(
                scope,
                DocumentArtifactStore.ArtifactType.ORIGINAL,
                "01J00000000000000000000003",
                "loan-policy.pdf"
        ));
        assertThat(store.exists(new DocumentArtifactStore.ArtifactReference(
                scope,
                DocumentArtifactStore.ArtifactType.ORIGINAL,
                "01J00000000000000000000003",
                "loan-policy.pdf"
        ))).isFalse();
    }

    @Test
    void shouldRejectUntrustedObjectKeyPartsAndCleanUpAfterUploadFailure() {
        RecordingOssStoreService ossStoreService = new RecordingOssStoreService();
        FonsOssDocumentArtifactStore store = new FonsOssDocumentArtifactStore(ossStoreService);
        DocumentArtifactStore.ArtifactScope scope = new DocumentArtifactStore.ArtifactScope(
                "../workspace",
                "01J00000000000000000000001",
                "01J00000000000000000000002"
        );

        assertThatThrownBy(() -> store.storeOriginal(new DocumentArtifactStore.OriginalArtifactRequest(
                scope,
                "loan-policy.txt",
                "text/plain",
                new ByteArrayInputStream("original content".getBytes(StandardCharsets.UTF_8))
        ))).isInstanceOf(RuntimeException.class);
        assertThat(ossStoreService.deletedObjectKeys).isEmpty();

        ossStoreService.failUpload = true;
        DocumentArtifactStore.ArtifactScope validScope = new DocumentArtifactStore.ArtifactScope(
                "01J00000000000000000000000",
                "01J00000000000000000000001",
                "01J00000000000000000000002"
        );
        assertThatThrownBy(() -> store.storeOriginal(new DocumentArtifactStore.OriginalArtifactRequest(
                validScope,
                "loan-policy.txt",
                "text/plain",
                new ByteArrayInputStream("original content".getBytes(StandardCharsets.UTF_8))
        ))).isInstanceOf(RuntimeException.class);
        assertThat(ossStoreService.deletedObjectKeys).containsKey(
                "workspaces/01J00000000000000000000000/knowledge-bases/01J00000000000000000000001/"
                        + "documents/01J00000000000000000000002/loan-policy.txt"
        );
    }

    private static final class RecordingOssStoreService implements OssStoreService {

        private final Map<String, StoredObject> objects = new LinkedHashMap<>();
        private final Map<String, String> deletedObjectKeys = new LinkedHashMap<>();
        private boolean failUpload;
        private int accessUrlCalls;

        @Override
        public OssObjectResponse upload(OssUploadRequest request) {
            if (failUpload) {
                throw new IllegalStateException("simulated storage failure");
            }
            try {
                objects.put(request.getObjectKey(), new StoredObject(
                        request.getInputStream().readAllBytes(),
                        Map.copyOf(request.getMetadata())
                ));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return OssObjectResponse.builder()
                    .objectKey(request.getObjectKey())
                    .metadata(request.getMetadata())
                    .build();
        }

        @Override
        public OssObjectResponse download(OssObjectRequest request) {
            StoredObject object = objects.get(request.getObjectKey());
            if (object == null) {
                throw new IllegalStateException("missing object");
            }
            return OssObjectResponse.builder()
                    .objectKey(request.getObjectKey())
                    .inputStream(new ByteArrayInputStream(object.content()))
                    .metadata(object.metadata())
                    .build();
        }

        @Override
        public boolean exists(OssObjectRequest request) {
            return objects.containsKey(request.getObjectKey());
        }

        @Override
        public void delete(OssObjectRequest request) {
            objects.remove(request.getObjectKey());
            deletedObjectKeys.put(request.getObjectKey(), request.getObjectKey());
        }

        @Override
        public OssObjectResponse getObjectInfo(OssObjectRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAccessUrl(OssObjectRequest request) {
            accessUrlCalls++;
            return "https://must-not-be-used.example/" + request.getObjectKey();
        }

        private record StoredObject(byte[] content, Map<String, String> metadata) {
        }
    }
}
