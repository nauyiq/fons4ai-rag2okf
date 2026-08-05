package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.identity.UserProfileApplicationService;
import com.fons.cloud.ai.rag2okf.common.response.UserProfileResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.common.result.R;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 当前用户资料 HTTP 契约测试。
 *
 * @author hongqy
 */
class UserProfileControllerTest {

    @Test
    void shouldWrapTheCurrentProfileInTheUnifiedResponse() {
        UserProfileApplicationService profileService = mock(UserProfileApplicationService.class);
        KbUserEntity user = new KbUserEntity();
        user.setUserKey("01JUSERKEY00000000000000001");
        user.setEmail("hongqy@example.com");
        user.setDisplayName("Hong QY");
        when(profileService.currentUser()).thenReturn(user);
        UserProfileController controller = new UserProfileController(profileService);

        R<UserProfileResponse> response = controller.currentUser();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().email()).isEqualTo("hongqy@example.com");
    }
}
