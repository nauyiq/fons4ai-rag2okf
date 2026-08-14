# KC-USER-002 Workspace 所有权与成员角色分离

> 知识编号：KC-USER-002  
> 知识类型：业务规则  
> 所属领域：用户域（`user`）  
> 状态：已验证  
> 来源：user  
> 可信度说明：2026-08-13 用户确认  
> 关联能力：Workspace 授权  
> 关联适配：全部 Workspace  
> 关联场景：BS-USER-005  
> 关联对象：Workspace、WorkspaceMember、WorkspaceRole  
> 关联代码/接口/SQL：`WorkspaceRole.covers()`  
> 更新日期：2026-08-13

## 1. 事实描述

- 核心事实：Workspace 有唯一所有者；所有者同时是 ADMIN，但所有权高风险动作不能仅凭 ADMIN 判断。
- 事实粒度：单一授权规则。
- 适用范围：所有 Workspace 与成员管理。
- 不适用范围：非 Workspace 资源。
- 证据依据：用户 Q4 按推荐。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 所有权 | Workspace 授权 | 全部 | 删除、移交等高风险操作校验 ownerUserId | 已验证 |
| 角色 | Workspace 授权 | 全部 | ADMIN/KNOWLEDGE_USER 使用显式权限策略，不使用 ordinal | 已验证 |

## 3. 技术落地

- 入口：Workspace 内业务操作
- 应用服务：WorkspaceAuthorizationService（目标）
- 领域对象/方法：WorkspacePermissionPolicy
- 仓储/Mapper：Workspace/Member Repository
- 外部协作：会话适配器
- 测试：所有者与 ADMIN 差异权限测试

## 4. 关联知识

- 业务文档：`../用户域业务文档.md`
- 技术文档：`../用户域技术文档.md`
- 数据文档：`../用户域数据文档.md`
- 相关卡片：KC-USER-001、KC-USER-003
