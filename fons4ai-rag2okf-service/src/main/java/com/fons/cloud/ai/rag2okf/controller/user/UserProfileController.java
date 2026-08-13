package com.fons.cloud.ai.rag2okf.controller.user;

import com.fons.cloud.ai.rag2okf.application.user.UserProfileApplicationService;
import com.fons.cloud.ai.rag2okf.common.request.UpdateUserProfileRequest;
import com.fons.cloud.ai.rag2okf.common.response.UserProfileResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceMember;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前本地用户资料 REST 入口。
 *
 * @author hongqy
 */
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileApplicationService userProfileApplicationService;

    /**
     * 获取当前用户的安全资料字段。
     *
     * @return 用户资料，不包含密码摘要和会话令牌
     */
    @GetMapping
    public R<UserProfileResponse> currentUser() {
        KbUser user = userProfileApplicationService.currentUser();
        KbWorkspace workspace = userProfileApplicationService.currentWorkspace(user);
        KbWorkspaceMember membership = userProfileApplicationService.currentMembership(user, workspace);
        return R.ok(UserProfileResponse.from(user, workspace, membership));
    }

    /**
     * 更新当前用户资料白名单。
     *
     * @param request 可修改资料字段
     * @return 更新后的用户资料
     */
    @PatchMapping
    public R<UserProfileResponse> updateCurrentUser(@RequestBody UpdateUserProfileRequest request) {
        KbUser user = userProfileApplicationService.updateCurrentUser(
                request.displayName(), request.avatarUrl(), request.preferenceJson());
        KbWorkspace workspace = userProfileApplicationService.currentWorkspace(user);
        KbWorkspaceMember membership = userProfileApplicationService.currentMembership(user, workspace);
        return R.ok(UserProfileResponse.from(user, workspace, membership));
    }

}
