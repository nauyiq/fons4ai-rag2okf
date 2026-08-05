package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.identity.AuthenticationApplicationService;
import com.fons.cloud.ai.rag2okf.application.identity.LoginCommand;
import com.fons.cloud.ai.rag2okf.common.request.LoginRequest;
import com.fons.cloud.ai.rag2okf.common.response.LoginResponse;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 本地邮箱密码会话 REST 入口。
 *
 * @author hongqy
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthSessionController {

    private final AuthenticationApplicationService authenticationApplicationService;
    /**
     * 使用已有本地账号建立 Header Bearer 会话。
     *
     * @param request 登录请求
     * @param servletRequest Servlet 请求
     * @return 仅在 data 中返回会话令牌的统一响应
     */
    @PostMapping("/login")
    public R<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        String token = authenticationApplicationService.login(new LoginCommand(
                request.email(), request.password(), request.rememberMe(), servletRequest.getRemoteAddr()
        ));
        return R.ok(new LoginResponse(token));
    }

    /**
     * 注销当前 Header Bearer 会话。
     *
     * @return 统一成功响应
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        authenticationApplicationService.logout();
        return R.ok();
    }

}
