package com.fons.cloud.ai.rag2okf.model;

import com.fons.cloud.ai.rag2okf.common.dto.CurrentUserContext;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import com.fons.cloud.ai.rag2okf.common.dto.ModelClientFactory;
import com.fons.cloud.ai.rag2okf.application.model.ModelConfigurationApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.ModelParameterCodec;
import com.fons.cloud.ai.rag2okf.common.dto.UserModelResolver;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProviderTemplate;
import com.fons.cloud.ai.rag2okf.common.response.ModelProviderTemplateResponse;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelConnectionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import com.fons.cloud.ai.rag2okf.infrastructure.model.AesGcmCredentialCipher;
import com.fons.cloud.ai.rag2okf.infrastructure.model.ModelEndpointPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OpenAI-compatible Provider 模板契约测试（T036）。
 *
 * <p>验证 P0 内置 Provider 模板与响应 DTO 的契约：
 * <ul>
 *   <li>ModelProviderTemplate 枚举包含 7 个值</li>
 *   <li>非 CUSTOM 模板的 defaultBaseUrl 使用 https 协议</li>
 *   <li>CUSTOM 模板的 defaultBaseUrl 为 null</li>
 *   <li>每个模板的 providerName 非空</li>
 *   <li>模板列表可通过 ModelConfigurationApplicationService.listTemplates() 返回</li>
 *   <li>ModelProviderTemplateResponse 只含 code、providerName、defaultBaseUrl、officialUrl，不含敏感字段</li>
 * </ul>
 *
 * @author hongqy
 */
class OpenAiCompatibleProviderContractTest {

    @Test
    void modelProviderTemplateShouldContainExactlyFiveValues() {
        ModelProviderTemplate[] values = ModelProviderTemplate.values();

        assertThat(values).hasSize(7);
        assertThat(values).containsExactly(
                ModelProviderTemplate.ALIYUN_DASHSCOPE,
                ModelProviderTemplate.DEEPSEEK,
                ModelProviderTemplate.OPENAI,
                ModelProviderTemplate.VOLCENGINE_ARK,
                ModelProviderTemplate.TENCENT_HUNYUAN,
                ModelProviderTemplate.ZHIPU_BIGMODEL,
                ModelProviderTemplate.CUSTOM);
    }

    @Test
    void nonCustomTemplatesShouldUseHttpsDefaultBaseUrl() {
        for (ModelProviderTemplate template : ModelProviderTemplate.values()) {
            if (template == ModelProviderTemplate.CUSTOM) {
                continue;
            }
            assertThat(template.getDefaultBaseUrl())
                    .as("%s 的 defaultBaseUrl 必须以 https 开头", template.name())
                    .startsWith("https://");
        }
    }

    @Test
    void customTemplateShouldHaveNullDefaultBaseUrl() {
        assertThat(ModelProviderTemplate.CUSTOM.getDefaultBaseUrl())
                .as("CUSTOM 模板的 defaultBaseUrl 必须为 null")
                .isNull();
    }

    @Test
    void eachTemplateShouldHaveNonBlankProviderName() {
        for (ModelProviderTemplate template : ModelProviderTemplate.values()) {
            assertThat(template.getProviderName())
                    .as("%s 的 providerName 不得为空", template.name())
                    .isNotBlank();
        }
    }

    @Test
    void listTemplatesShouldReturnAllProviderTemplates() {
        ModelConfigurationApplicationService service = serviceForTemplateListing();

        List<ModelProviderTemplateResponse> templates = service.listTemplates();

        assertThat(templates).hasSize(7);

        List<ModelProviderTemplate> codes = templates.stream()
                .map(ModelProviderTemplateResponse::code)
                .toList();
        assertThat(codes).containsExactlyElementsOf(Arrays.asList(ModelProviderTemplate.values()));
    }

    @Test
    void modelProviderTemplateResponseShouldOnlyContainNonSensitiveFields() {
        List<String> components = Arrays.stream(ModelProviderTemplateResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .as("ModelProviderTemplateResponse 只应包含 code、providerName、defaultBaseUrl、officialUrl 四个字段")
                .containsExactly("code", "providerName", "defaultBaseUrl", "officialUrl");

        List<String> sensitive = components.stream()
                .filter(name -> name.toLowerCase().contains("apikey")
                        || name.toLowerCase().contains("secret")
                        || name.toLowerCase().contains("password")
                        || name.toLowerCase().contains("ciphertext")
                        || name.toLowerCase().contains("nonce"))
                .toList();

        assertThat(sensitive)
                .as("ModelProviderTemplateResponse 不得包含 apiKey 或其他敏感字段")
                .isEmpty();
    }

    private ModelConfigurationApplicationService serviceForTemplateListing() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        ModelParameterCodec parameterCodec = new ModelParameterCodec(new ObjectMapper());
        UserModelResolver userModelResolver = new UserModelResolver(
                mock(KbModelConnectionDomainService.class),
                mock(KbModelProfileDomainService.class),
                new ModelEndpointPolicy(),
                parameterCodec);

        return new ModelConfigurationApplicationService(
                mock(CurrentUserContext.class),
                mock(KbModelConnectionDomainService.class),
                mock(KbModelProfileDomainService.class),
                new AesGcmCredentialCipher(key, "v1", "rag2okf"),
                (ModelBusinessKeyGenerator) () -> "01JMODELKEY000000000000000",
                new ModelEndpointPolicy(),
                parameterCodec,
                userModelResolver,
                mock(ModelClientFactory.class));
    }
}
