package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/** 解析任务快照不可变契约测试。 */
class ParseTaskSnapshotTest {

    @Test
    void shouldDefensivelyCopyRequestedAt() {
        Date requestedAt = new Date(123L);
        ParseTaskSnapshot snapshot = ParseTaskSnapshot.of(
                "workspace", "knowledge-base", "document", "file",
                ParserType.BUILT_IN, Map.of(),
                new ChunkPolicy(
                        ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, Map.of()),
                Map.of(), false, 1L, requestedAt);

        requestedAt.setTime(456L);
        Date exposed = snapshot.requestedAt();
        exposed.setTime(789L);

        assertEquals(123L, snapshot.requestedAt().getTime());
        assertNotSame(exposed, snapshot.requestedAt());
    }
}
