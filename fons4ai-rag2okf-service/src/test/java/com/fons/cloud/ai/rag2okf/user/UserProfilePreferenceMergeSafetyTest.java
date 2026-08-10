package com.fons.cloud.ai.rag2okf.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.application.identity.UserProfileApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.ai.rag2okf.controller.UserProfileController;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMapper;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMemberMapper;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.LocalAccountRepository;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * preferenceJson 局部合并安全回归（T033）。
 *
 * <p>连续 PATCH 复用同一持久化实体，覆盖 Service 与 Controller 调用边界，确保未提交的顶层偏好键不丢失。</p>
 *
 * @author hongqy
 */
class UserProfilePreferenceMergeSafetyTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private KbUserEntity user;
    private UserProfileApplicationService service;
    private UserProfileController controller;

    @BeforeEach
    void setUp() {
        LocalAccountRepository accountRepository = mock(LocalAccountRepository.class);
        SaTokenAuthTemplate saToken = mock(SaTokenAuthTemplate.class);
        KbWorkspaceMapper workspaceMapper = mock(KbWorkspaceMapper.class);
        KbWorkspaceMemberMapper workspaceMemberMapper = mock(KbWorkspaceMemberMapper.class);
        user = new KbUserEntity();
        user.setId(10L);
        user.setUserKey("01JUSERKEY00000000000000001");
        user.setEmail("preference-test@example.com");
        user.setDisplayName("偏好测试用户");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferenceJson("{\"theme\":\"dark\",\"language\":\"zh-CN\",\"density\":\"compact\"}");
        when(saToken.isLogin()).thenReturn(true);
        when(saToken.getCurrentLoginIdAsString()).thenReturn(user.getUserKey());
        when(accountRepository.findByUserKey(user.getUserKey())).thenReturn(Optional.of(user));
        service = new UserProfileApplicationService(accountRepository, saToken, workspaceMapper, workspaceMemberMapper);
        controller = new UserProfileController(service);
    }

    @Test
    @DisplayName("五组连续 PATCH 仅替换已提交顶层节点，未修改偏好始终保留")
    void shouldPreserveUntouchedKeysAcrossFivePatchCombinations() throws Exception {
        patch("{\"defaultModels\":{\"defaults\":{\"LLM\":\"profile-1\"}}}");
        assertPreference("theme", "dark");
        assertPreference("language", "zh-CN");

        patch("{\"theme\":\"light\"}");
        assertPreference("theme", "light");
        assertPreference("defaultModels.defaults.LLM", "profile-1");

        patch("{\"language\":\"en-US\"}");
        assertPreference("language", "en-US");
        assertPreference("density", "compact");

        patch("{\"density\":\"comfortable\",\"defaultModels\":{\"defaults\":{\"EMBEDDING\":\"profile-2\"}}}");
        assertPreference("theme", "light");
        assertPreference("defaultModels.defaults.EMBEDDING", "profile-2");

        patch("{\"sidebar\":{\"collapsed\":true}}");
        assertPreference("language", "en-US");
        assertPreference("density", "comfortable");
        assertPreference("sidebar.collapsed", true);
    }

    @Test
    @DisplayName("空对象、非 object 与非法 JSON 均不破坏既有偏好")
    void shouldIgnoreEmptyAndNonObjectPatches() {
        String existing = user.getPreferenceJson();

        patch("{}");
        assertThat(user.getPreferenceJson()).isEqualTo(existing);
        patch("[]");
        assertThat(user.getPreferenceJson()).isEqualTo(existing);
        patch("\"not-an-object\"");
        assertThat(user.getPreferenceJson()).isEqualTo(existing);
        patch("{broken-json");
        assertThat(user.getPreferenceJson()).isEqualTo(existing);
    }

    @Test
    @DisplayName("首次 PATCH 也只接受非空 JSON object")
    void firstPatchShouldRejectInvalidRootAndAcceptObject() throws Exception {
        user.setPreferenceJson(null);

        patch("{}");
        assertThat(user.getPreferenceJson()).isNull();
        patch("[]");
        assertThat(user.getPreferenceJson()).isNull();
        patch("null");
        assertThat(user.getPreferenceJson()).isNull();
        patch("\"not-an-object\"");
        assertThat(user.getPreferenceJson()).isNull();
        patch("{broken-json");
        assertThat(user.getPreferenceJson()).isNull();

        patch("{\"theme\":\"dark\"}");
        assertPreference("theme", "dark");
    }

    @Test
    @DisplayName("Controller PATCH 返回的是合并后的安全偏好快照")
    void controllerShouldReturnMergedPreferenceSnapshot() throws Exception {
        var response = controller.updateCurrentUser(new com.fons.cloud.ai.rag2okf.common.request.UpdateUserProfileRequest(
                "偏好测试用户", null, "{\"defaultModels\":{\"defaults\":{\"LLM\":\"profile-1\"}}}"));

        JsonNode preference = mapper.readTree(response.getData().preferenceJson());
        assertThat(preference.path("theme").asText()).isEqualTo("dark");
        assertThat(preference.path("language").asText()).isEqualTo("zh-CN");
        assertThat(preference.path("defaultModels").path("defaults").path("LLM").asText())
                .isEqualTo("profile-1");
    }

    @Test
    @DisplayName("Controller 仅 PATCH preferenceJson 时保留展示名称与头像")
    void controllerShouldSupportPreferenceOnlyPatch() throws Exception {
        var response = controller.updateCurrentUser(new com.fons.cloud.ai.rag2okf.common.request.UpdateUserProfileRequest(
                null, null, "{\"defaultModels\":{\"defaults\":{\"LLM\":\"profile-1\"}}}"));

        assertThat(response.getData().displayName()).isEqualTo("偏好测试用户");
        assertThat(response.getData().avatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertPreference("defaultModels.defaults.LLM", "profile-1");
    }

    private void patch(String preferenceJson) {
        service.updateCurrentUser(user.getDisplayName(), user.getAvatarUrl(), preferenceJson);
    }

    private void assertPreference(String dottedPath, Object expected) throws Exception {
        JsonNode node = mapper.readTree(user.getPreferenceJson());
        for (String segment : dottedPath.split("\\.")) {
            node = node.path(segment);
        }
        if (expected instanceof Boolean booleanValue) {
            assertThat(node.asBoolean()).isEqualTo(booleanValue);
        } else {
            assertThat(node.asText()).isEqualTo(expected);
        }
    }
}
