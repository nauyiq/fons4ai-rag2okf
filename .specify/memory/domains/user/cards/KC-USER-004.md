# KC-USER-004 邮箱密码是唯一认证标准

> 知识编号：KC-USER-004  
> 知识类型：业务规则  
> 所属领域：用户域（`user`）  
> 状态：已验证  
> 来源：user/docs/code  
> 可信度说明：用户选择 B，与当前 SDD/源码一致  
> 关联能力：邮箱密码认证  
> 关联适配：BA-USER-001  
> 关联场景：BS-USER-001、BS-USER-002  
> 关联对象：LocalUser  
> 关联代码/接口/SQL：`kb_user.email`、`kb_user.password_hash`  
> 更新日期：2026-08-13

## 1. 事实描述

- 核心事实：Rag2OKF 用户域只以规范化邮箱+密码进行认证；其他认证方式需通过需求变更引入。
- 事实粒度：单一认证标准。
- 适用范围：全部本地用户注册与登录。
- 不适用范围：未来经变更确认的外部身份。
- 证据依据：用户 Q5 选择 B。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 认证标识 | 认证 | 邮箱密码 | 规范化后 email 全局唯一 | 已验证 |
| 密码安全 | 认证 | 邮箱密码 | 仅保存不可逆随机盐摘要 | 已验证 |

## 3. 技术落地

- 入口：`/auth/registration`、`/auth/login`
- 应用服务：UserAuthApplicationService
- 领域对象/方法：LocalUser
- 仓储/Mapper：LocalUserRepository（目标）
- 外部协作：PasswordHasher、RateLimiter、SessionAdapter
- 测试：认证安全特征测试

## 4. 关联知识

- 业务文档：`../用户域业务文档.md`
- 技术文档：`../用户域技术文档.md`
- 数据文档：`../用户域数据文档.md`
- 相关卡片：KC-USER-001
