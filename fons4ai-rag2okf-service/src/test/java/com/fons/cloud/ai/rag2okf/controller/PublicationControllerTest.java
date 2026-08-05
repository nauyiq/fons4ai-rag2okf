package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.publication.PublicationApplicationService;
import com.fons.cloud.ai.rag2okf.common.response.PublicationResponse;
import com.fons.cloud.common.result.R;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PublicationController HTTP 契约测试。
 *
 * @author hongqy
 */
class PublicationControllerTest {

    @Test
    void shouldReturnUnifiedResponseForManualPublish() {
        PublicationApplicationService service = mock(PublicationApplicationService.class);
        when(service.triggerPublish(eq("01J_DOC"), anyString()))
                .thenReturn(new PublicationResponse("01J_DOC", "01J_TASK", "PUBLISHING", "QUEUED"));
        PublicationController controller = new PublicationController(service);

        R<PublicationResponse> response = controller.triggerPublish("01J_DOC", null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().documentKey()).isEqualTo("01J_DOC");
        assertThat(response.getData().taskKey()).isEqualTo("01J_TASK");
        assertThat(response.getData().publishStatus()).isEqualTo("PUBLISHING");
    }

    @Test
    void shouldPassTriggerTypeToService() {
        PublicationApplicationService service = mock(PublicationApplicationService.class);
        when(service.triggerPublish("01J_DOC", "AUTO"))
                .thenReturn(new PublicationResponse("01J_DOC", "01J_TASK", "PUBLISHED", "QUEUED"));
        PublicationController controller = new PublicationController(service);

        R<PublicationResponse> response = controller.triggerPublish("01J_DOC", "AUTO");

        assertThat(response.getData().publishStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void shouldDefaultTriggerTypeToManualWhenAbsent() {
        PublicationApplicationService service = mock(PublicationApplicationService.class);
        when(service.triggerPublish("01J_DOC", "MANUAL"))
                .thenReturn(new PublicationResponse("01J_DOC", "01J_TASK", "PUBLISHING", "QUEUED"));
        PublicationController controller = new PublicationController(service);

        controller.triggerPublish("01J_DOC", null);

        // 验证默认传入 MANUAL
        org.mockito.Mockito.verify(service).triggerPublish("01J_DOC", "MANUAL");
    }
}
