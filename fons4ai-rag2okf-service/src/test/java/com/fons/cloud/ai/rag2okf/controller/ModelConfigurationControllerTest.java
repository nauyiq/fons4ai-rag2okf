package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.model.ModelConfigurationApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProviderTemplate;
import com.fons.cloud.ai.rag2okf.common.response.ModelProviderTemplateResponse;
import com.fons.cloud.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 模型配置 HTTP 统一响应契约测试。
 *
 * @author hongqy
 */
class ModelConfigurationControllerTest {

    @Test
    void shouldWrapProviderTemplatesInTheFons4CloudResponseEnvelope() {
        ModelConfigurationApplicationService applicationService = mock(ModelConfigurationApplicationService.class);
        when(applicationService.listTemplates()).thenReturn(List.of(
                new ModelProviderTemplateResponse(ModelProviderTemplate.CUSTOM, "自定义", null)
        ));
        ModelConfigurationController controller = new ModelConfigurationController(applicationService);

        R<List<ModelProviderTemplateResponse>> response = controller.listTemplates();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().getFirst().code()).isEqualTo(ModelProviderTemplate.CUSTOM);
    }
}
