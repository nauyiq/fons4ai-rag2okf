package com.fons.cloud.ai.rag2okf.controller.document;

import com.fons.cloud.ai.rag2okf.application.document.DocumentApplicationService;
import com.fons.cloud.ai.rag2okf.application.document.DocumentParseApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingTaskStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentParseRequest;
import com.fons.cloud.ai.rag2okf.common.request.document.ChunkPolicyRequest;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentParseResponse;
import com.fons.cloud.common.result.R;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-009 手动解析唯一 HTTP 路由与字段映射契约测试。 */
class DocumentControllerParseTest {

    @Test
    void shouldExposeOnlyFormalDocumentParseRouteAndDelegate() throws Exception {
        DocumentApplicationService documentService = mock(DocumentApplicationService.class);
        DocumentParseApplicationService parseService = mock(DocumentParseApplicationService.class);
        DocumentController controller = new DocumentController(documentService, parseService);
        DocumentParseRequest request = new DocumentParseRequest(
                ParserType.BUILT_IN,
                new ChunkPolicyRequest(ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, Map.of()), false);
        DocumentParseResponse expected = new DocumentParseResponse(
                "task-key", ResultStage.INIT, ParserType.BUILT_IN, ProcessingTaskStatus.QUEUED);
        when(parseService.startParse("doc-key", request)).thenReturn(R.ok(expected));

        R<DocumentParseResponse> actual = controller.parseDocument("doc-key", request);

        Method method = DocumentController.class.getMethod(
                "parseDocument", String.class, DocumentParseRequest.class);
        assertArrayEquals(new String[]{"/documents/{documentKey}/parse"},
                method.getAnnotation(PostMapping.class).value());
        assertEquals(expected, actual.getData());
        verify(parseService).startParse("doc-key", request);
    }
}
