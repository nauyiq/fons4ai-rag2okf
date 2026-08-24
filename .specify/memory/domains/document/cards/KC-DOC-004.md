# KC-DOC-004 内置解析模型能力容错

> 知识编号：KC-DOC-004  
> 知识类型：业务适配  
> 所属领域：文档域（document）  
> 状态：已验证  
> 来源：user/code  
> 可信度说明：目标能力和容错规则由用户确认；当前客户端覆盖不完整。  
> 关联能力：文档解析  
> 关联适配：Built-in Parser  
> 关联场景：BS-DOC-003  
> 关联对象：ModelCapabilityGateway、ParseRevision  
> 关联代码/接口/SQL：`LangChain4jModelClientFactory.java`（差距证据）  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：内置解析器自动规划 LLM、EMBEDDING、RERANK、ASR、VLM、OCR 等能力；必要能力失败使解析失败，增强能力失败允许带警告成功。
- 事实粒度：单一适配规则
- 适用范围：Built-in Parser。
- 不适用范围：MinerU 内部能力、知识库绑定所有权。
- 证据依据：2026-08-14 用户确认。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 能力分级 | 文档解析 | Built-in | OCR/VLM互补、不静默替代；能力集合可扩展 | 已验证 |

## 3. 技术落地

- 入口：PARSE Task。
- 应用服务：BuiltInParseWorkflow。
- 领域对象/方法：能力计划、Normalizer、Validator。
- 仓储/Mapper：ParseRevision/Task Repository。
- 外部协作：知识库 ModelBinding、ModelCapabilityGateway。
- 测试：各能力缺失/调用失败、增强警告、结构化输出校验。

## 4. 关联知识

- 业务文档：`../文档域业务文档.md`
- 技术文档：`../文档域技术文档.md`
- 数据文档：`../文档域数据文档.md`
- 相关卡片：KC-DOC-002、KC-DOC-005

