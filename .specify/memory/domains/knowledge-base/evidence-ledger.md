# 证据账本

> 领域名称：知识库域  
> 领域标识：`knowledge-base`  
> 更新日期：2026-08-14

| 结论 | 证据文件/资料 | 证据类型 | 覆盖适配对象 | 是否可作为标准 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 正式名称和slug为知识库域/`knowledge-base` | `.specify/memory/index.md`+本次用户请求 | 已有知识库+用户确认 | 全部 | 是 | 名称未变更 |
| Workspace/成员/角色归用户域，知识库域只引用授权边界 | 2026-08-14 Q1按推荐+用户域文档 | 用户确认+已有知识库 | 全部 | 是 | 修正项目级旧口径 |
| KnowledgeBase生命周期、默认策略、ModelBinding是三类核心能力 | 2026-08-14 Q2按推荐 | 用户确认 | 全部 | 是 | Retrieval Profile/OKF开关仅规划候选 |
| autoParse/autoPublish允许四种组合 | `docs/product-design/02-业务流程与状态模型.md`+Q3 | 正式文档+用户确认 | BA-KB-001～004 | 是 | false/true表示手动解析成功后自动发布 |
| 当前代码和UI禁止false/true | `KbKnowledgeBase.java`、两个知识库Vue视图 | 源码事实 | BA-KB-002 | 否 | 明确实现偏差，不得作为标准 |
| 设置变化不追溯已有文档 | SDD BR-011、项目业务BR-018、应用服务注释 | 正式文档+源码 | BA-KB-001～004 | 是 | 快照时点仍待确认 |
| ModelBinding归知识库域且按用途引用Profile | Q1/Q2/Q4+SDD+`KbModelBinding.java` | 用户确认+正式文档+源码 | BA-KB-005/006 | 是 | 不复制凭证 |
| 同知识库同用途唯一 | `init-schema.sql`+KnowledgeBaseApplicationService | 数据库事实+源码 | BA-KB-005/006 | 是 | 当前SQL有唯一约束 |
| ANSWER_GENERATION需要LLM，EMBEDDING需要Embedding及当前维度 | `ModelUsageType.java`+应用服务 | 源码事实 | BA-KB-005/006 | 当前适用 | 当前用途与维度不是永久封闭标准 |
| 只有创建者可删除，二次确认且重复删除幂等 | 20260807需求+源码+Q5 | 正式文档+源码+用户确认 | 全部 | 是 | 跨资源删除治理除外 |
| 当前仅软删除知识库主记录且不清理下游 | KnowledgeBaseApplicationService.deleteKnowledgeBase | 源码事实 | 删除 | 否 | 数据治理未闭合 |
| Java/MyBatis/Vue和具体接口是代表性实现 | 2026-08-14 Q5按推荐+当前源码 | 用户确认+源码 | 全部 | 否 | 不定义领域标准 |
| 当前后端测试树未见知识库域测试 | `fons4ai-rag2okf-service/src/test`当前文件清单 | 代码库事实 | 全部 | 否 | 前端有契约/视图测试，后端护栏待补 |
| 缺少EMBEDDING绑定的行为存在文档冲突 | 需求fail-closed规则与技术设计BM25-only描述 | 正式文档冲突 | BA-KB-006 | 待确认 | 不自行选择一方 |

