# KC-USER-001 用户域负责 Workspace 与成员协作

> 知识编号：KC-USER-001  
> 知识类型：治理规则  
> 所属领域：用户域（`user`）  
> 状态：已验证  
> 来源：user  
> 可信度说明：2026-08-13 用户明确选择  
> 关联能力：Workspace 协作与授权  
> 关联适配：个人空间、协作空间  
> 关联场景：BS-USER-004、BS-USER-005  
> 关联对象：Workspace、WorkspaceMember  
> 关联代码/接口/SQL：`kb_workspace`、`kb_workspace_member`  
> 更新日期：2026-08-13

## 1. 事实描述

- 核心事实：Workspace 与 WorkspaceMember 均归用户域；知识库域只引用 Workspace 隔离边界。
- 事实粒度：单一领域归属规则。
- 适用范围：全部 Workspace 及下游业务授权。
- 不适用范围：KnowledgeBase 自身生命周期。
- 证据依据：用户 Q1 选择 B。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 数据所有权 | Workspace 协作 | 全部 | 用户域管理空间和成员，下游通过端口鉴权 | 已验证 |

## 3. 技术落地

- 入口：下游业务访问
- 应用服务：WorkspaceAuthorizationService（目标）
- 领域对象/方法：Workspace、WorkspaceMember
- 仓储/Mapper：目标 Repository
- 外部协作：知识库、文档、OKF、检索域
- 测试：跨 Workspace 越权集成测试

## 4. 关联知识

- 业务文档：`../用户域业务文档.md`
- 技术文档：`../用户域技术文档.md`
- 数据文档：`../用户域数据文档.md`
- 相关卡片：KC-USER-002、KC-USER-003
