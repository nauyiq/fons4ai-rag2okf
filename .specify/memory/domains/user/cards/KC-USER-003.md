# KC-USER-003 Workspace 邀请与成员关系分离

> 知识编号：KC-USER-003  
> 知识类型：状态流转  
> 所属领域：用户域（`user`）  
> 状态：已验证  
> 来源：user  
> 可信度说明：目标标准已确认，当前尚未实现  
> 关联能力：Workspace 协作  
> 关联适配：BA-USER-003  
> 关联场景：BS-USER-004  
> 关联对象：WorkspaceInvitation、WorkspaceMember  
> 关联代码/接口/SQL：待 SDD  
> 更新日期：2026-08-13

## 1. 事实描述

- 核心事实：WorkspaceInvitation 承载未完成的邀请过程，WorkspaceMember 只表示已生效或已停用的成员关系。
- 事实粒度：单一状态模型规则。
- 适用范围：协作空间邀请。
- 不适用范围：个人空间。
- 证据依据：用户 Q3 按推荐。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 邀请 | Workspace 协作 | 协作空间 | PENDING 可转 ACCEPTED/REJECTED/REVOKED/EXPIRED | 已验证 |
| 成员 | Workspace 协作 | 协作空间 | 接受邀请后才创建 ACTIVE Member | 已验证 |

## 3. 技术落地

- 入口：待 SDD
- 应用服务：WorkspaceCollaborationApplicationService（目标）
- 领域对象/方法：WorkspaceInvitation.accept/reject/revoke/expire（目标语义）
- 仓储/Mapper：Invitation/Member Repository
- 外部协作：通知渠道待确认
- 测试：状态机与并发接受

## 4. 关联知识

- 业务文档：`../用户域业务文档.md`
- 技术文档：`../用户域技术文档.md`
- 数据文档：`../用户域数据文档.md`
- 相关卡片：KC-USER-001、KC-USER-002
