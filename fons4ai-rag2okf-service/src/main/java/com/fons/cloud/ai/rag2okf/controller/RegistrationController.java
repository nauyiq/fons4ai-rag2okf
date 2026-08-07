package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.identity.RegistrationApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.RegistrationCommand;
import com.fons.cloud.ai.rag2okf.common.request.RegistrationRequest;
import com.fons.cloud.ai.rag2okf.common.response.LoginResponse;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 邮箱密码注册 REST 入口。
 *
 * @author hongqy
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationApplicationService registrationApplicationService;

    /**
     * 注册新本地账号并建立会话。
     *
     * @param request 注册请求
     * @param servletRequest Servlet 请求
     * @return 仅在 data 中返回会话令牌的统一响应
     */
    @PostMapping("/registration")
    public R<LoginResponse> register(
            @RequestBody RegistrationRequest request,
            HttpServletRequest servletRequest
    ) {
        String token = registrationApplicationService.register(new RegistrationCommand(
                request.email(), request.password(), request.confirmPassword(),
                request.displayName(), servletRequest.getRemoteAddr()
        ));
        return R.ok(new LoginResponse(token));
    }
}
