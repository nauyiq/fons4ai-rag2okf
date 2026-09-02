package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkPolicy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fastjson2 ChunkManifest 编解码与稳定映射顺序测试。 */
class ChunkManifestCodecTest {

    private static final String DOCUMENT_KEY = "document-key";
    private static final String RESULT_KEY = "result-key";
    private static final String CONTENT_HASH = "content-hash";

    @Test
    void shouldEncodeMapsStablyAndReadManifest() {
        ChunkManifestCodec codec = new ChunkManifestCodec();
        ChunkManifest first = manifest(Map.of("zeta", 2, "alpha", 1));
        Map<String, Object> reverseOrderParameters = new LinkedHashMap<>();
        reverseOrderParameters.put("alpha", 1);
        reverseOrderParameters.put("zeta", 2);

        byte[] firstJson = codec.writeJson(first);
        byte[] secondJson = codec.writeJson(manifest(reverseOrderParameters));
        ChunkManifest decoded = codec.readJson(firstJson);

        assertArrayEquals(firstJson, secondJson);
        assertEquals(DOCUMENT_KEY, decoded.getDocumentKey());
        assertEquals(RESULT_KEY, decoded.getResultKey());
        assertEquals(CONTENT_HASH, decoded.getContentHash());
        assertEquals(1, decoded.getChunkPolicy().parameters().get("alpha"));
        assertEquals(2, decoded.getChunkPolicy().parameters().get("zeta"));
    }

    private ChunkManifest manifest(Map<String, Object> parameters) {
        ChunkManifest manifest = new ChunkManifest();
        manifest.setDocumentKey(DOCUMENT_KEY);
        manifest.setResultKey(RESULT_KEY);
        manifest.setChunkPolicy(new ChunkPolicy(
                ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, parameters));
        manifest.setContentHash(CONTENT_HASH);
        return manifest;
    }
}
