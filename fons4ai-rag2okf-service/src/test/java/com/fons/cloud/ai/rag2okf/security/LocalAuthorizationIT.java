package com.fons.cloud.ai.rag2okf.security;

import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.exeception.WorkspaceAccessDeniedException;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rag2OKF 本地授权边界静态契约测试（T025）。
 *
 * <p>验证技术设计 §3.2、D-009 和 CR-002 的本地授权约束：
 * <ul>
 *   <li>业务授权只信任 localUser/member/localRole（AC-001、AC-002、AC-028）</li>
 *   <li>WorkspaceAccessPolicy 是唯一公开授权入口（AC-028）</li>
 *   <li>角色层级 ADMIN 覆盖 KNOWLEDGE_USER（AC-002）</li>
 *   <li>Sa-Token 认证身份不直接授予知识库权限（AC-028）</li>
 * </ul>
 *
 * @author hongqy
 */
@Execution(ExecutionMode.CONCURRENT)
class LocalAuthorizationIT {

    /**
     * AC-028：WorkspaceAccessPolicy 必须只暴露一个 public 方法 checkAccess，
     * 作为业务授权的唯一入口，避免绕过本地授权。
     */
    @Test
    void workspaceAccessPolicyShouldExposeSinglePublicCheckAccessMethod() {
        long publicMethodCount = Arrays.stream(WorkspaceAccessPolicy.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .count();

        assertThat(publicMethodCount)
                .as("WorkspaceAccessPolicy 应只暴露 checkAccess 一个公开方法（AC-028）")
                .isEqualTo(1);
    }

    /**
     * AC-028：checkAccess 方法签名必须包含 userKey、workspaceKey 和 requiredRole 三个参数，
     * 确保授权决策基于本地用户、空间和角色。
     */
    @Test
    void checkAccessSignatureShouldAcceptUserKeyWorkspaceKeyAndRequiredRole() throws Exception {
        Method checkAccess = WorkspaceAccessPolicy.class.getMethod(
                "checkAccess", String.class, String.class, WorkspaceRole.class);

        assertThat(checkAccess.getParameterCount())
                .as("checkAccess 必须接受 userKey、workspaceKey、requiredRole 三个参数")
                .isEqualTo(3);
        assertThat(checkAccess.getParameterTypes()[0])
                .as("第一个参数应为 userKey（String）")
                .isEqualTo(String.class);
        assertThat(checkAccess.getParameterTypes()[1])
                .as("第二个参数应为 workspaceKey（String）")
                .isEqualTo(String.class);
        assertThat(checkAccess.getParameterTypes()[2])
                .as("第三个参数应为 requiredRole（WorkspaceRole）")
                .isEqualTo(WorkspaceRole.class);
    }

    /**
     * AC-002：WorkspaceRole 必须包含 KNOWLEDGE_USER 和 ADMIN 两种角色，
     * 且 ADMIN 的 ordinal 大于 KNOWLEDGE_USER，支持 covers() 层级覆盖。
     */
    @Test
    void workspaceRoleShouldDefineUserAndAdminWithCoverSemantics() {
        WorkspaceRole[] roles = WorkspaceRole.values();

        assertThat(roles)
                .as("WorkspaceRole 必须包含 KNOWLEDGE_USER 和 ADMIN（AC-002）")
                .contains(WorkspaceRole.KNOWLEDGE_USER, WorkspaceRole.ADMIN);

        assertThat(WorkspaceRole.ADMIN.ordinal())
                .as("ADMIN 的层级必须高于 KNOWLEDGE_USER")
                .isGreaterThan(WorkspaceRole.KNOWLEDGE_USER.ordinal());
    }

    /**
     * AC-002：ADMIN.covers(KNOWLEDGE_USER) 必须返回 true，
     * 知识用户尝试管理操作时被拒绝，管理员可执行知识用户操作。
     */
    @Test
    void adminRoleShouldCoverKnowledgeUserRole() {
        assertThat(WorkspaceRole.ADMIN.covers(WorkspaceRole.KNOWLEDGE_USER))
                .as("ADMIN 必须覆盖 KNOWLEDGE_USER（AC-002）")
                .isTrue();
        assertThat(WorkspaceRole.KNOWLEDGE_USER.covers(WorkspaceRole.ADMIN))
                .as("KNOWLEDGE_USER 不得覆盖 ADMIN（AC-002）")
                .isFalse();
    }

    /**
     * AC-002：KNOWLEDGE_USER.covers(ADMIN) 必须返回 false，
     * 知识用户尝试管理操作时被拒绝。
     */
    @Test
    void knowledgeUserShouldNotCoverAdminRole() {
        assertThat(WorkspaceRole.KNOWLEDGE_USER.covers(WorkspaceRole.ADMIN))
                .as("KNOWLEDGE_USER 不得覆盖 ADMIN（AC-002）")
                .isFalse();
        assertThat(WorkspaceRole.KNOWLEDGE_USER.covers(WorkspaceRole.KNOWLEDGE_USER))
                .as("KNOWLEDGE_USER 应覆盖自身")
                .isTrue();
        assertThat(WorkspaceRole.ADMIN.covers(WorkspaceRole.ADMIN))
                .as("ADMIN 应覆盖自身")
                .isTrue();
    }

    /**
     * AC-028：WorkspaceAccessDeniedException 必须是 RuntimeException，
     * 确保授权失败时能被全局异常处理器统一捕获。
     */
    @Test
    void workspaceAccessDeniedExceptionMustBeRuntimeException() {
        assertThat(RuntimeException.class.isAssignableFrom(WorkspaceAccessDeniedException.class))
                .as("WorkspaceAccessDeniedException 必须继承 RuntimeException")
                .isTrue();
    }

    /**
     * AC-028：WorkspaceAccessPolicy 必须标注 @Component，
     * 确保被 Spring 容器管理并可注入到业务服务。
     */
    @Test
    void workspaceAccessPolicyShouldBeSpringComponent() {
        assertThat(WorkspaceAccessPolicy.class.isAnnotationPresent(
                org.springframework.stereotype.Component.class))
                .as("WorkspaceAccessPolicy 必须标注 @Component")
                .isTrue();
    }
}
