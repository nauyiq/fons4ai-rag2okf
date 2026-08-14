# 证据账本

> 领域名称：用户域  
> 领域标识：`user`  
> 更新日期：2026-08-13

| 结论 | 证据文件/资料 | 证据类型 | 覆盖适配对象 | 是否可作为标准 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 用户域正式名称与 slug 为用户域/`user` | `.specify/memory/index.md` + 2026-08-13 用户启动建模 | 已有知识库+用户确认 | 全部 | 是 | 此次未改名 |
| Workspace 与 WorkspaceMember 都归用户域 | 2026-08-13 Q1 选择 B | 用户确认 | 全部 Workspace | 是 | 修正了项目基线中 Workspace 归知识库域的旧口径 |
| ModelConnection/ModelProfile 归用户域，ModelBinding 归知识库域 | 2026-08-13 Q2 按推荐 | 用户确认 | 模型资源 | 是 | 凭证不跨域泄漏 |
| WorkspaceInvitation 与 WorkspaceMember 分离 | 2026-08-13 Q3 按推荐 | 用户确认 | 协作空间 | 是 | 目标标准，尚未实现 |
| Workspace 采用唯一所有者+成员角色双层模型 | 2026-08-13 Q4 按推荐 | 用户确认 | 全部 Workspace | 是 | 所有者同时为 ADMIN，高风险动作单独校验 owner |
| 角色权限不得依赖 enum ordinal | 2026-08-13 Q4 + `WorkspaceRole.covers()` | 用户确认+源码冲突 | 全部 Workspace | 是 | 当前实现是需治理偏差 |
| 邮箱密码是唯一认证标准 | 2026-08-13 Q5 选择 B | 用户确认 | 认证 | 是 | 其他认证方式属需求变更 |
| 登录身份与 Workspace 业务授权分层 | `.specify/memory/项目业务架构文档.md`、SDD、`WorkspaceAccessPolicy.java` | 正式文档+源码 | 邮箱密码/Workspace | 是 | 不信任浏览器传入的角色和空间 |
| 注册原子创建 User + Personal Workspace + ADMIN Member | SDD + `UserAuthApplicationService.java` | 正式文档+源码 | 邮箱密码/个人空间 | 是 | 当前代码已实现基本流程 |
| 模型资源是用户域核心能力，协议为适配点 | 2026-08-13 Q6 按推荐 | 用户确认 | 模型资源 | 是 | Provider 模板不是领域对象 |
| OpenAI-compatible 只是代表性实现 | Q6 + 当前单协议源码 | 用户确认+源码 | OpenAI-compatible | 否 | 没有横向实现支撑定义通用标准 |
| API Key 加密保存且零明文回显 | SDD、`KbModelConnectionEntity.java`、`init-schema.sql` | 正式文档+源码+数据库候选事实 | OpenAI-compatible | 是 | 具体 AES-GCM 类是代表性技术实现 |
| 当前后端测试已删除 | Git `a12d93e` + 当前无 `src/test` | 代码库事实 | 全部 | 否 | 建模后重构前必须恢复特征测试 |
| 项目级业务/数据架构仍有 Workspace 旧归属 | `.specify/memory/项目业务架构文档.md`、`.../项目数据架构文档.md` | 文档冲突 | Workspace | 否 | 本次只更新领域文档与索引，待显式知识汇总统一治理 |
