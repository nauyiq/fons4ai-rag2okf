# KC-KB-001 知识库域拥有知识库聚合但不拥有Workspace

> 知识编号：KC-KB-001  
> 知识类型：治理规则  
> 所属领域：知识库域（`knowledge-base`）  
> 状态：已验证  
> 来源：user/docs/code  
> 可信度说明：2026-08-14用户确认，且与用户域已建模边界一致  
> 关联能力：知识库生命周期  
> 关联适配：全部  
> 关联场景：BS-KB-001～004  
> 关联对象：KnowledgeBase、WorkspaceAccessBoundary  
> 关联代码/接口/SQL：`kb_knowledge_base.workspace_id`  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：知识库域拥有KnowledgeBase生命周期；Workspace、成员和角色归用户域，知识库域只引用归属并消费授权判定。
- 事实粒度：单一领域归属规则。
- 适用范围：全部知识库读写和下游知识空间上下文。
- 不适用范围：Workspace协作、邀请和角色管理。
- 证据依据：用户Q1按推荐；用户域知识文档；当前workspaceId引用。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 领域边界 | 知识库生命周期 | 全部 | 通过跨域授权边界协作，不复制成员/角色 | 已验证 |

## 3. 技术落地

- 入口：知识库HTTP接口
- 应用服务：KnowledgeBaseApplicationService
- 领域对象/方法：KnowledgeBase；目标WorkspaceAccessBoundary
- 仓储/Mapper：KnowledgeBaseRepository目标抽象
- 外部协作：用户域
- 测试：Workspace越权API集成测试待补齐

## 4. 关联知识

- 业务文档：`../知识库域业务文档.md`
- 技术文档：`../知识库域技术文档.md`
- 数据文档：`../知识库域数据文档.md`
- 相关卡片：KC-KB-005

