# KC-DOC-006 分块策略两个正交维度

> 知识编号：KC-DOC-006  
> 知识类型：业务规则  
> 所属领域：文档域（document）  
> 状态：已验证  
> 来源：user  
> 可信度说明：策略分类与默认组合由用户确认，参数待评测。  
> 关联能力：分块  
> 关联适配：LENGTH/STRUCTURE/SEMANTIC、FLAT/PARENT_CHILD  
> 关联场景：BS-DOC-005/006  
> 关联对象：ChunkRevision、ChunkManifest  
> 关联代码/接口/SQL：目标 ChunkBoundaryStrategy/ChunkHierarchyStrategy  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：分块由边界 LENGTH/STRUCTURE/SEMANTIC 与层级 FLAT/PARENT_CHILD 两个正交维度组成，默认 STRUCTURE+PARENT_CHILD。
- 事实粒度：单一规则
- 适用范围：首次分块与重新分块。
- 不适用范围：具体阈值、检索排序。
- 证据依据：2026-08-14 用户确认。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 策略组合 | 分块 | 全部 | 语义依赖Embedding；长度限制不改变策略；表格保结构并可重复表头 | 已验证 |

## 3. 技术落地

- 入口：PARSE/RECHUNK Task。
- 应用服务：ChunkWorkflow。
- 领域对象/方法：BoundaryStrategy、HierarchyStrategy。
- 仓储/Mapper：ChunkRevision Repository。
- 外部协作：SEMANTIC 使用 ModelCapabilityGateway。
- 测试：六种组合、表格、阈值、安全长度、父子完整性。

## 4. 关联知识

- 业务文档：`../文档域业务文档.md`
- 技术文档：`../文档域技术文档.md`
- 数据文档：`../文档域数据文档.md`
- 相关卡片：KC-DOC-003、KC-DOC-005、KC-DOC-007

