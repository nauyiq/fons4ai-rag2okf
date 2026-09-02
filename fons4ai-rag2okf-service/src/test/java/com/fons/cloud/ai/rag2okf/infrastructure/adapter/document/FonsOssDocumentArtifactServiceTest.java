package com.fons.cloud.ai.rag2okf.infrastructure.adapter.document;

import com.fons.cloud.file.api.OssStoreService;
import com.fons.cloud.file.common.request.OssUploadRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** ParsedDocument 不可变对象路径与双制品写入测试。 */
class FonsOssDocumentArtifactServiceTest {

    @Test
    void shouldStoreJsonAndMarkdownUnderResultKey() {
        OssStoreService store = mock(OssStoreService.class);
        FonsOssDocumentArtifactService service = new FonsOssDocumentArtifactService(store);
        String key = "01HZX3Y7Q9K2M4N6P8R0T2V4W6";

        FonsOssDocumentArtifactService.StoredParsedArtifacts result = service.storeParsed(
                new FonsOssDocumentArtifactService.ParsedArtifactCommand(
                        key, key, key, key, "{}".getBytes(), "# parsed".getBytes()));

        assertEquals("workspaces/%s/knowledge-bases/%s/documents/%s/parses/%s/parsed-document.v1.json"
                .formatted(key, key, key, key), result.jsonObjectKey());
        assertEquals("workspaces/%s/knowledge-bases/%s/documents/%s/parses/%s/parsed-document.md"
                .formatted(key, key, key, key), result.markdownObjectKey());
        verify(store, times(2)).upload(any(OssUploadRequest.class));
    }
}
