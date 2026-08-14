# KC-KB-002 默认处理策略允许四种自动化组合

> 知识编号：KC-KB-002  
> 知识类型：业务规则  
> 所属领域：知识库域（`knowledge-base`）  
> 状态：已验证  
> 来源：user/docs/code  
> 可信度说明：正式产品文档和2026-08-14用户确认；当前代码有已标记偏差  
> 关联能力：默认处理策略  
> 关联适配：BA-KB-001～004  
> 关联场景：BS-KB-005  
> 关联对象：DefaultProcessingPolicy  
> 关联代码/接口/SQL：`auto_parse`、`auto_publish`  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：autoParse和autoPublish分别控制解析与发布自动化，允许false/false、false/true、true/false、true/true四种组合。
- 事实粒度：单一配置组合规则。
- 适用范围：知识库默认处理策略及文档操作的有效策略。
- 不适用范围：解析和发布流水线的执行细节。
- 证据依据：产品设计02和用户Q3按推荐。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 配置组合 | 默认处理策略 | 四种模式 | false/true表示手动解析成功后自动发布 | 已验证 |
| 实现偏差 | 默认处理策略 | false/true | 当前实体和UI错误拒绝，不得写成标准 | 已验证 |

## 3. 技术落地

- 入口：知识库创建/编辑；文档操作
- 应用服务：目标DefaultProcessingPolicyPort
- 领域对象/方法：DefaultProcessingPolicy
- 仓储/Mapper：KnowledgeBaseRepository
- 外部协作：文档域
- 测试：四组合领域测试与false/true跨域集成测试待补齐

## 4. 关联知识

- 业务文档：`../知识库域业务文档.md`
- 技术文档：`../知识库域技术文档.md`
- 数据文档：`../知识库域数据文档.md`
- 相关卡片：KC-KB-003、KC-KB-006

