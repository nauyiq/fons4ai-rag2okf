package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.parsing.ParseApplicationService;
import com.fons.cloud.ai.rag2okf.common.response.ParseTriggerResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 手动解析 Controller 契约测试。
 *
 * @author hongqy
 */
class DocumentParseControllerTest {

    @Test
    void manualParseDefaultsToExplicitParseMode() {
        ParseApplicationService service = mock(ParseApplicationService.class);
        when(service.triggerParse("01J_DOC", "PARSE"))
                .thenReturn(new ParseTriggerResponse(
                        "01J_DOC", "01J_TASK", "QUEUED", "UNPUBLISHED", null));
        DocumentParseController controller = new DocumentParseController(service);

        controller.triggerParse("01J_DOC", null);

        verify(service).triggerParse("01J_DOC", "PARSE");
    }
}
