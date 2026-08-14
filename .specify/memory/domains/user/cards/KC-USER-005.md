# KC-USER-005 用户模型资源与知识库用途绑定分离

> 知识编号：KC-USER-005  
> 知识类型：治理规则  
> 所属领域：用户域（`user`）  
> 状态：已验证  
> 来源：user/docs/code  
> 可信度说明：用户 Q2/Q6 确认，与当前数据结构一致  
> 关联能力：模型资源管理  
> 关联适配：BA-USER-004  
> 关联场景：BS-USER-006  
> 关联对象：ModelConnection、ModelProfile、ModelBinding  
> 关联代码/接口/SQL：`kb_model_connection`、`kb_model_profile`、`kb_model_binding`  
> 更新日期：2026-08-13

## 1. 事实描述

- 核心事实：ModelConnection 与 ModelProfile 归用户域；ModelBinding 归知识库域。知识库域不得读取或解密 API Key。
- 事实粒度：单一跨域所有权规则。
- 适用范围：模型连接、档案、用途绑定和实际模型调用。
- 不适用范围：模型 Provider 的外部账号体系。
- 证据依据：用户 Q2/Q6 按推荐。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 数据所有权 | 模型资源 | 全部协议 | 用户域拥有连接、凭证和档案 | 已验证 |
| 跨域引用 | 知识库配置 | 全部协议 | 知识库只持有 profile 引用与 usage | 已验证 |
| 凭证边界 | 模型调用 | 全部协议 | 下游只经受控解析端口获取能力 | 已验证 |

## 3. 技术落地

- 入口：模型配置 API + 内部模型解析端口
- 应用服务：ModelConfigurationApplicationService
- 领域对象/方法：ModelConnection、ModelProfile
- 仓储/Mapper：Connection/Profile Repository
- 外部协作：ModelProfileValidationPort、ModelCapabilityResolutionPort
- 测试：跨用户越权、凭证零泄漏、无全局回退

## 4. 关联知识

- 业务文档：`../用户域业务文档.md`
- 技术文档：`../用户域技术文档.md`
- 数据文档：`../用户域数据文档.md`
- 相关卡片：KC-USER-006
