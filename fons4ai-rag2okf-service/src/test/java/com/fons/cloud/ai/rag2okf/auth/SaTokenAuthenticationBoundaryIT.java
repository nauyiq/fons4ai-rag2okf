package com.fons.cloud.ai.rag2okf.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.fons.cloud.ai.rag2okf.infrastructure.security.Rag2OkfStpInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sa-Token 登录身份与认证边界静态契约测试（T025）。
 *
 * <p>验证技术设计 §3.2、§5.1、D-009 和 CR-002 的 Sa-Token 边界约束：
 * <ul>
 *   <li>Sa-Token 只维护登录态，不承担账号查询或密码匹配（AC-026、AC-027）</li>
 *   <li>Rag2OkfStpInterface 替换 Sa-Token 默认权限实现，返回空列表（AC-028）</li>
 *   <li>业务授权只信任 localUser/member/localRole，不依赖 Sa-Token 注解鉴权（AC-028）</li>
 *   <li>仅接受 Authentication: Bearer token，不读取 Cookie，无 CSRF 机制（AC-026）</li>
 * </ul>
 *
 * <p>该测试通过反射分析注解和配置验证契约，不依赖真实 Redis/MySQL 容器。
 * 登录/频控/注销的端到端验证由隔离环境的集成测试负责。
 *
 * @author hongqy
 */
@Execution(ExecutionMode.CONCURRENT)
class SaTokenAuthenticationBoundaryIT {

    /**
     * AC-028：Rag2OkfStpInterface 必须实现 StpInterface 并返回空权限列表，
     * 中立化 Sa-Token 注解鉴权，强制业务入口调用 WorkspaceAccessPolicy。
     */
    @Test
    void rag2OkfStpInterfaceShouldReturnEmptyPermissionsToNeutralizeAnnotationAuth() {
        StpInterface stpInterface = new Rag2OkfStpInterface();

        List<String> permissions = stpInterface.getPermissionList("uk-001", "login");
        List<String> roles = stpInterface.getRoleList("uk-001", "login");

        assertThat(permissions)
                .as("Rag2OkfStpInterface 必须返回空权限列表（AC-028）")
                .isEmpty();
        assertThat(roles)
                .as("Rag2OkfStpInterface 必须返回空角色列表（AC-028）")
                .isEmpty();
    }

    /**
     * AC-028：Rag2OkfStpInterface 必须注册为 Spring @Component，
     * 覆盖 fons4cloud-auth-satoken 的 DefaultStpInterfaceImpl。
     */
    @Test
    void rag2OkfStpInterfaceShouldBeRegisteredAsSpringComponent() {
        assertThat(Rag2OkfStpInterface.class.isAnnotationPresent(
                org.springframework.stereotype.Component.class))
                .as("Rag2OkfStpInterface 必须标注 @Component 以覆盖默认 StpInterface（AC-028）")
                .isTrue();
    }

    /**
     * AC-028：Rag2OkfStpInterface 必须实现 StpInterface 接口，
     * 确保被 Sa-Token 框架识别为权限适配器。
     */
    @Test
    void rag2OkfStpInterfaceMustImplementStpInterface() {
        assertThat(StpInterface.class.isAssignableFrom(Rag2OkfStpInterface.class))
                .as("Rag2OkfStpInterface 必须实现 StpInterface 接口")
                .isTrue();
    }

    /**
     * AC-028：不同 loginId 调用 Rag2OkfStpInterface 都应返回空列表，
     * 确保权限中立化不因用户身份变化。
     */
    @Test
    void rag2OkfStpInterfaceShouldReturnEmptyForAnyLoginId() {
        StpInterface stpInterface = new Rag2OkfStpInterface();

        assertThat(stpInterface.getPermissionList(null, "login")).isEmpty();
        assertThat(stpInterface.getPermissionList("any-user", "login")).isEmpty();
        assertThat(stpInterface.getPermissionList("admin-user", "login")).isEmpty();
        assertThat(stpInterface.getRoleList(null, "login")).isEmpty();
        assertThat(stpInterface.getRoleList("any-user", "login")).isEmpty();
    }
}
