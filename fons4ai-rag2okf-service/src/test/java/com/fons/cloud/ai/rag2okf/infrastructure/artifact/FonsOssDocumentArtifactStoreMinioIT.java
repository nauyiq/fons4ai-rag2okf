package com.fons.cloud.ai.rag2okf.infrastructure.artifact;

import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore;
import com.fons.cloud.file.api.OssStoreService;
import com.fons.cloud.file.common.CloudSecret;
import com.fons.cloud.file.common.constants.ServerProvider;
import com.fons.cloud.file.common.request.OssObjectRequest;
import com.fons.cloud.file.common.response.OssObjectResponse;
import com.fons.cloud.file.core.oss.MinioOssStoreService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 隔离 MinIO 容器中的文档产物存储集成测试。
 *
 * @author hongqy
 */
@Testcontainers
class FonsOssDocumentArtifactStoreMinioIT {

    private static final String ACCESS_KEY = "rag2okf-test-access";
    private static final String SECRET_KEY = "rag2okf-test-secret";
    private static final String BUCKET = "rag2okf-artifact-test";

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-02-18T16-25-55Z")
    )
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withExposedPorts(9000)
            .withCommand("server", "/data");

    private static DocumentArtifactStore artifactStore;
    private static OssStoreService ossStoreService;

    @BeforeAll
    static void setUpBucket() throws Exception {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        MinioClient minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }
        CloudSecret cloudSecret = new CloudSecret();
        cloudSecret.setProvider(ServerProvider.MINIO);
        cloudSecret.setEndpoint(endpoint);
        cloudSecret.setBucket(BUCKET);
        cloudSecret.setSecretId(ACCESS_KEY);
        cloudSecret.setSecretKey(SECRET_KEY);
        ossStoreService = new MinioOssStoreService(cloudSecret);
        artifactStore = new FonsOssDocumentArtifactStore(ossStoreService);
    }

    @Test
    void shouldStreamOriginalAndManifestThroughFonsOssServiceWithPrivateMetadata() throws Exception {
        DocumentArtifactStore.ArtifactScope scope = scope();
        DocumentArtifactStore.StoredArtifact original = artifactStore.storeOriginal(
                new DocumentArtifactStore.OriginalArtifactRequest(
                        scope,
                        "policy.txt",
                        "text/plain",
                        new ByteArrayInputStream("private policy".getBytes(StandardCharsets.UTF_8))
                )
        );
        DocumentArtifactStore.StoredArtifact manifest = artifactStore.storeManifest(
                new DocumentArtifactStore.ManifestArtifactRequest(
                        scope,
                        DocumentArtifactStore.ArtifactType.PARSED_MANIFEST,
                        "01J00000000000000000000004",
                        new ByteArrayInputStream("{\"blocks\":[]}".getBytes(StandardCharsets.UTF_8))
                )
        );

        assertThat(original.objectKey()).doesNotContain("policy.txt", "..", "\\");
        assertThat(original.sha256()).hasSize(64);
        assertThat(manifest.objectKey()).endsWith("parsed-manifest.json");
        OssObjectResponse objectInfo = ossStoreService.getObjectInfo(
                OssObjectRequest.builder().objectKey(original.objectKey()).build()
        );
        assertThat(objectInfo.getMetadata()).containsEntry("original-filename", "policy.txt")
                .containsEntry("content-type", "text/plain");

        try (InputStream inputStream = artifactStore.open(new DocumentArtifactStore.ArtifactReference(
                scope,
                DocumentArtifactStore.ArtifactType.ORIGINAL,
                "01J00000000000000000000003"
        )).inputStream()) {
            assertThat(inputStream.readAllBytes()).isEqualTo("private policy".getBytes(StandardCharsets.UTF_8));
        }

        artifactStore.delete(new DocumentArtifactStore.ArtifactReference(
                scope,
                DocumentArtifactStore.ArtifactType.ORIGINAL,
                "01J00000000000000000000003"
        ));
        assertThat(artifactStore.exists(new DocumentArtifactStore.ArtifactReference(
                scope,
                DocumentArtifactStore.ArtifactType.ORIGINAL,
                "01J00000000000000000000003"
        ))).isFalse();
    }

    @Test
    void shouldRemoveTheTargetObjectWhenTheUploadStreamFails() {
        DocumentArtifactStore.ArtifactScope scope = scope();
        DocumentArtifactStore.ArtifactReference reference = new DocumentArtifactStore.ArtifactReference(
                scope,
                DocumentArtifactStore.ArtifactType.ORIGINAL,
                "01J00000000000000000000005"
        );

        assertThatThrownBy(() -> artifactStore.storeOriginal(new DocumentArtifactStore.OriginalArtifactRequest(
                scope,
                "failed.txt",
                "text/plain",
                failingStream()
        ))).isInstanceOf(RuntimeException.class);
        assertThat(artifactStore.exists(reference)).isFalse();
    }

    private static DocumentArtifactStore.ArtifactScope scope() {
        return new DocumentArtifactStore.ArtifactScope(
                "01J00000000000000000000000",
                "01J00000000000000000000001",
                "01J00000000000000000000002"
        );
    }

    private static InputStream failingStream() {
        return new InputStream() {
            private boolean firstRead = true;

            @Override
            public int read() throws IOException {
                if (firstRead) {
                    firstRead = false;
                    return 'x';
                }
                throw new IOException("simulated stream failure");
            }
        };
    }
}
