package com.fons.cloud.ai.rag2okf.controller.user;

import com.fons.cloud.ai.rag2okf.application.user.UserAuthApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.model.user.LoginCommand;
import com.fons.cloud.ai.rag2okf.common.model.user.RegistrationCommand;
import com.fons.cloud.ai.rag2okf.common.request.user.LoginRequest;
import com.fons.cloud.ai.rag2okf.common.request.user.RegistrationRequest;
import com.fons.cloud.ai.rag2okf.common.response.user.LoginResponse;
import com.fons.cloud.common.result.R;
import jakarta.validation.Valid;
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

    private final UserAuthApplicationService userAuthApplicationService;

    /**
     * 使用已有本地账号建立 Header Bearer 会话。
     *
     * @param request 登录请求
     * @param servletRequest Servlet 请求
     * @return 仅在 data 中返回会话令牌的统一响应
     */
    @PostMapping("/login")
    public R<LoginResponse> login(
            @RequestBody @Valid LoginRequest request, HttpServletRequest servletRequest) {
        R<String> result = userAuthApplicationService.login(new LoginCommand(request.email(), request.password(), request.rememberMe(), servletRequest.getRemoteAddr()));
        return result.isSuccess() ? R.ok(new LoginResponse(result.getData())) : R.failed(result);
    }

    /**
     * 注销当前 Header Bearer 会话。
     *
     * @return 统一成功响应
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        userAuthApplicationService.logout();
        return R.ok();
    }

    /**
     * 注册新本地账号并建立会话。
     *
     * @param request 注册请求
     * @param servletRequest Servlet 请求
     * @return 仅在 data 中返回会话令牌的统一响应
     */
    @PostMapping("/registration")
    public R<LoginResponse> register(@RequestBody @Valid RegistrationRequest request, HttpServletRequest servletRequest) {
        if (!request.password().equals(request.confirmPassword())) {
            return R.failed(Rag2OkfResultCode.DUPLICATE_PASSWORD_INCORRECT);
        }
        R<String> result = userAuthApplicationService.register(new RegistrationCommand(request.email(), request.password(), request.confirmPassword(), request.displayName(), servletRequest.getRemoteAddr()));
        return result.isSuccess() ? R.ok(new LoginResponse(result.getData())) : R.failed(result);
    }

}
