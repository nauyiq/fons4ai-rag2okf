package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.identity.UserProfileApplicationService;
import com.fons.cloud.ai.rag2okf.application.knowledgebase.KnowledgeBaseApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T033 Controller 层 HTTP 集成回归。
 *
 * <p>启动最小 Spring Web 容器并替换外部依赖，验证偏好局部 PATCH 的响应边界和
 * 知识库非创建者删除的 403 异常映射，不访问 MySQL、Redis 或模型服务。</p>
 *
 * @author hongqy
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = T033ControllerIntegrationTest.TestApplication.class,
        properties = {
                "spring.config.name=t033-controller-integration",
                "sys.sa-token.global-login-check=false"
        }
)
@AutoConfigureMockMvc
@DisplayName("T033 Controller 集成：偏好合并与知识库删除鉴权")
class T033ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileApplicationService userProfileApplicationService;

    @MockitoBean
    private KnowledgeBaseApplicationService knowledgeBaseApplicationService;

    @Test
    @DisplayName("仅提交 preferenceJson 时 HTTP 响应保留资料字段和未修改偏好")
    void preferenceOnlyPatchShouldReturnMergedProfileSnapshot() throws Exception {
        KbUserEntity mergedUser = new KbUserEntity();
        mergedUser.setUserKey("01JUSERKEY00000000000000001");
        mergedUser.setEmail("preference-test@example.com");
        mergedUser.setDisplayName("偏好测试用户");
        mergedUser.setAvatarUrl("https://example.com/avatar.png");
        mergedUser.setPreferenceJson("{\"theme\":\"dark\",\"language\":\"zh-CN\","
                + "\"defaultModels\":{\"defaults\":{\"LLM\":\"profile-1\"}}}");
        when(userProfileApplicationService.updateCurrentUser(
                null, null, "{\"defaultModels\":{\"defaults\":{\"LLM\":\"profile-1\"}}}"))
                .thenReturn(mergedUser);

        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferenceJson\":\"{\\\"defaultModels\\\":{\\\"defaults\\\":"
                                + "{\\\"LLM\\\":\\\"profile-1\\\"}}}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayName").value("偏好测试用户"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.preferenceJson").value(mergedUser.getPreferenceJson()));
    }

    @Test
    @DisplayName("非创建者 DELETE 知识库时 HTTP 层返回 403 NOT_PERMISSION")
    void nonOwnerDeleteShouldReturnForbidden() throws Exception {
        doThrow(new WorkspaceAccessDeniedException())
                .when(knowledgeBaseApplicationService).deleteKnowledgeBase(anyString());

        mockMvc.perform(delete("/knowledge-bases/01J_KB_OTHER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_PERMISSION.getCode()));
    }

    /**
     * 仅装配目标 Controller、统一异常端点和 Web 基础设施。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure.class,
            com.fons.cloud.db.autoconfigure.DatabaseConfiguration.class,
            com.fons.cloud.db.autoconfigure.MybatisPlusAutoConfiguration.class,
            com.fons.cloud.ai.agent.infrastructure.config.AgentAutoConfiguration.class,
            com.fons.cloud.ai.agent.langchain.config.LangChain4jAgentAutoConfiguration.class,
            com.fons.cloud.lock.config.DistributeLockAutoConfiguration.class,
            org.redisson.spring.starter.RedissonAutoConfigurationV2.class,
            com.fons.cloud.cache.config.IRedisAutoConfiguration.class,
            com.fons.cloud.cache.config.CacheAutoConfiguration.class
    })
    @Import({
            UserProfileController.class,
            KnowledgeBaseController.class,
            com.fons.cloud.ai.rag2okf.controller.endpoint.Rag2OkfExceptionEndpoint.class
    })
    static class TestApplication {
    }
}
