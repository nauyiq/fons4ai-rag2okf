# KC-DOC-003 PARSE任务串联首次分块

> 知识编号：KC-DOC-003  
> 知识类型：状态流转  
> 所属领域：文档域（document）  
> 状态：已验证  
> 来源：user  
> 可信度说明：用户确认解析与分块设计独立，但首次解析任务内串联。  
> 关联能力：解析编排、分块  
> 关联适配：全部解析器/分块策略  
> 关联场景：BS-DOC-002/005  
> 关联对象：ParseRevision、ChunkRevision、ProcessingTask  
> 关联代码/接口/SQL：`ParseTaskExecutor.java`（现状参考）  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：PARSE 任务必须完成解析和首次分块后才成功，并同时切换当前 ParseRevision 与 ChunkRevision 指针。
- 事实粒度：单一状态流转
- 适用范围：首次解析与重新解析。
- 不适用范围：独立 RECHUNK 任务。
- 证据依据：2026-08-14 用户确认。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 联合激活 | 解析/分块 | 全部 | 分块失败则整个任务失败；不激活解析中间物 | 已验证 |

## 3. 技术落地

- 入口：Parse API/PARSE Task。
- 应用服务：ParseTaskOrchestrator。
- 领域对象/方法：ParseRevision、ChunkRevision 联合激活。
- 仓储/Mapper：Parse/Chunk/SourceDocument Repository。
- 外部协作：ArtifactStore、分块策略。
- 测试：解析成功分块失败、同快照重试复用、指针原子切换。

## 4. 关联知识

- 业务文档：`../文档域业务文档.md`
- 技术文档：`../文档域技术文档.md`
- 数据文档：`../文档域数据文档.md`
- 相关卡片：KC-DOC-001、KC-DOC-005、KC-DOC-006

