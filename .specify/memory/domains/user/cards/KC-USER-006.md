# KC-USER-006 OpenAI-compatible 是代表性模型协议适配

> 知识编号：KC-USER-006  
> 知识类型：业务适配  
> 所属领域：用户域（`user`）  
> 状态：已验证  
> 来源：user/code  
> 可信度说明：用户 Q6 确认，当前仅有该协议实现  
> 关联能力：模型资源管理  
> 关联适配：BA-USER-004  
> 关联场景：BS-USER-006  
> 关联对象：ModelProtocolAdapter  
> 关联代码/接口/SQL：`ModelProtocolType.OPENAI_COMPATIBLE`  
> 更新日期：2026-08-13

## 1. 事实描述

- 核心事实：OpenAI-compatible 是当前模型协议的代表性适配，不是用户域公共标准；Provider 模板仅为创建连接的预设。
- 事实粒度：单一适配判定。
- 适用范围：当前 ModelConnection/ModelProfile 实现。
- 不适用范围：未来其他模型协议。
- 证据依据：用户 Q6 按推荐，且无第二个协议可横向对比。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 公共骨架 | 模型资源 | 全部协议 | 所有权、凭证安全、端点安全、能力测试 | 已验证 |
| 特定实现 | 模型资源 | OpenAI-compatible | 具体 endpoint 和请求协议 | 已验证 |

## 3. 技术落地

- 入口：模型连接/档案管理与测试
- 应用服务：ModelConfigurationApplicationService
- 领域对象/方法：ModelProtocolAdapter（目标）
- 仓储/Mapper：Connection/Profile Repository
- 外部协作：OpenAI-compatible Provider
- 测试：协议契约、SSRF、redirect、timeout、错误脱敏

## 4. 关联知识

- 业务文档：`../用户域业务文档.md`
- 技术文档：`../用户域技术文档.md`
- 数据文档：`../用户域数据文档.md`
- 相关卡片：KC-USER-005
