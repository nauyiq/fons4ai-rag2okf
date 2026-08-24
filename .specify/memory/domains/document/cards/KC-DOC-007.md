# KC-DOC-007 发布阶段Embedding失败关闭

> 知识编号：KC-DOC-007  
> 知识类型：业务规则  
> 所属领域：文档域（document）  
> 状态：已验证  
> 来源：user/docs/code  
> 可信度说明：用户关闭了旧文档与当前实现关于BM25-only的冲突。  
> 关联能力：RAG发布  
> 关联适配：全部  
> 关联场景：BS-DOC-007  
> 关联对象：PublicationRevision、ChunkRevision  
> 关联代码/接口/SQL：`PublicationTaskExecutor.java`（冲突证据）  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：缺少有效 EMBEDDING、模型调用失败或向量维度不匹配时发布失败，不得静默发布 BM25-only；旧活动发布保持可用。
- 事实粒度：单一规则
- 适用范围：文档 RAG 发布。
- 不适用范围：未来显式定义的 BM25-only 发布/检索模式。
- 证据依据：2026-08-14 用户确认。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| Fail-closed | RAG发布 | 全部 | 仅显式skipEmbedding的Chunk可无向量；投影成功后才切活动指针 | 已验证 |

## 3. 技术落地

- 入口：Publication API/PUBLISH Task。
- 应用服务：PublicationApplicationService。
- 领域对象/方法：PublicationRevision。
- 仓储/Mapper：Publication/Outbox/SourceDocument Repository。
- 外部协作：ModelCapabilityGateway、PublicationProjectionPort。
- 测试：绑定缺失、调用错误、维度、首发失败、旧发布保留、幂等。

## 4. 关联知识

- 业务文档：`../文档域业务文档.md`
- 技术文档：`../文档域技术文档.md`
- 数据文档：`../文档域数据文档.md`
- 相关卡片：KC-DOC-001、KC-DOC-006、KC-DOC-008

