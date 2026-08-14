# KC-KB-004 模型档案按用途绑定且不复制凭证

> 知识编号：KC-KB-004  
> 知识类型：业务适配  
> 所属领域：知识库域（`knowledge-base`）  
> 状态：已验证  
> 来源：user/docs/code/db  
> 可信度说明：用户确认、SDD、源码和SQL结构一致  
> 关联能力：模型用途绑定  
> 关联适配：BA-KB-005、BA-KB-006  
> 关联场景：BS-KB-006  
> 关联对象：ModelBinding、ModelProfile  
> 关联代码/接口/SQL：`kb_model_binding`  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：ModelBinding归知识库域并按usageType引用用户域ModelProfile；同一知识库同一用途最多一个有效绑定，绑定不复制Provider连接或API Key。
- 事实粒度：单一跨域绑定规则。
- 适用范围：当前ANSWER_GENERATION/EMBEDDING及未来正式启用的模型用途。
- 不适用范围：模型连接、档案、凭证和具体协议的管理。
- 证据依据：用户Q1/Q2/Q4、SDD、KbModelBinding、init-schema.sql。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 用途唯一 | 模型用途绑定 | 全部 | knowledgeBaseId+usageType唯一 | 已验证 |
| 类型兼容 | 模型用途绑定 | ANSWER/EMBEDDING | LLM或Embedding，后者含维度约束 | 已验证 |
| 凭证边界 | 模型用途绑定 | 全部 | 只保存profileId和非敏感参数 | 已验证 |

## 3. 技术落地

- 入口：model-bindings GET/PUT
- 应用服务：KnowledgeBaseApplicationService
- 领域对象/方法：KbModelBinding、ModelUsageType
- 仓储/Mapper：KbModelBindingDomainService/Mapper代表实现
- 外部协作：目标ModelProfileValidationPort
- 测试：后端唯一性、越权、类型、维度和事务测试待补齐

## 4. 关联知识

- 业务文档：`../知识库域业务文档.md`
- 技术文档：`../知识库域技术文档.md`
- 数据文档：`../知识库域数据文档.md`
- 相关卡片：KC-KB-006

