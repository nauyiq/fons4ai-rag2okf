package com.fons.cloud.ai.rag2okf.infrastructure.security;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rag2OKF 的 Sa-Token 权限适配器。
 *
 * <p>工作空间角色带有资源范围，不能在此处映射为全局 ADMIN 权限；业务入口必须调用
 * WorkspaceAccessPolicy 进行 workspaceKey 级别授权。</p>
 *
 * @author hongqy
 */
@Component
public class Rag2OkfStpInterface implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return List.of();
    }
}
