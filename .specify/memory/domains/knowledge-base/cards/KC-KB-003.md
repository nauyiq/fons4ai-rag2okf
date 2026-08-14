# KC-KB-003 知识库设置不追溯重处理已有文档

> 知识编号：KC-KB-003  
> 知识类型：业务规则  
> 所属领域：知识库域（`knowledge-base`）  
> 状态：已验证  
> 来源：user/docs/code  
> 可信度说明：正式SDD、源码注释和用户确认一致  
> 关联能力：默认处理策略  
> 关联适配：BA-KB-001～004  
> 关联场景：BS-KB-003、BS-KB-005  
> 关联对象：KnowledgeBase、DefaultProcessingPolicy  
> 关联代码/接口/SQL：KnowledgeBaseApplicationService  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：修改知识库默认策略只影响未来上传或后续明确发起的操作，不自动批量重处理已有文档。
- 事实粒度：单一策略生效范围规则。
- 适用范围：autoParse、autoPublish、Parser Profile、Chunk Profile及未来正式纳入的默认策略。
- 不适用范围：管理员明确发起的重新解析、重新分块或重新发布。
- 证据依据：BR-011/BR-018、应用服务注释、用户Q2/Q4。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 生效范围 | 默认处理策略 | 全部 | 设置修改不是隐式批量任务 | 已验证 |
| 快照治理 | 默认处理策略 | 全部 | 操作级快照的形成时点仍待确认 | 待确认 |

## 3. 技术落地

- 入口：PATCH知识库设置
- 应用服务：KnowledgeBaseApplicationService
- 领域对象/方法：KnowledgeBase.applyUpdate
- 仓储/Mapper：KnowledgeBaseRepository
- 外部协作：文档域任务输入
- 测试：设置修改不创建任务和历史任务输入不变的集成测试

## 4. 关联知识

- 业务文档：`../知识库域业务文档.md`
- 技术文档：`../知识库域技术文档.md`
- 数据文档：`../知识库域数据文档.md`
- 相关卡片：KC-KB-002

