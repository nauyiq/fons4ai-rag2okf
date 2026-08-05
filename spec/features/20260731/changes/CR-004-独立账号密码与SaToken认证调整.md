# CR-004 独立账号密码与 Sa-Token 认证调整

> 功能标识：`knowledge-base-document-lifecycle`
> 变更类型：重构
> SDD 等级：`S2`
> 文档状态：正式
> 创建日期：2026-07-31

## 1. 变更摘要

- 变更一句话说明：Rag2OKF 不再接入 `fons4cloud-auth` 与 Communication Dubbo，改为自身维护账号密码并复用 `fons4cloud-auth-satoken` 的独立会话和鉴权能力。
- 本次变更结论：采用用户确认的方案 A，保留自助注册页，注册改为账号、密码、确认密码和展示名称；Sa-Token 只承担会话、登录校验、Token 管理和权限扩展，账号查询、密码摘要校验、注册事务及工作空间授权归属 Rag2OKF。
- 是否建议新建 feature：否。知识库文档生命周期与页面范围不变，认证相关 AC 和技术路径发生局部高风险重构，未超过 70%。

## 2. 变更原因

- 用户诉求：知识库不采用 Fons4Cloud 统一认证体系，使用 Fons4Cloud 独立 Sa-Token 能力；登录改为账号密码，知识库拥有自身安全认证体系。
- 业务或技术背景：仓库中的 `fons4cloud-auth-satoken` 明确面向“不接入认证服务与网关的单点应用”，提供登录、会话校验和 Token 管理，并复用 Common Cache Redis；它不负责账号存储或密码校验。
- 不变更的影响：继续保留 Auth/Communication Dubbo 会造成认证边界与用户最新决策冲突，也会同时存在远程 token、本地 session 和短信频控三套不必要状态。

## 3. 当前状态检查

- 任务规划未完成项：T001～T027 均未执行；本次更新受影响任务并追加 T028。
- 历史 CR 未完成项：CR-001～CR-003 均未进入实现；CR-003 的手机号验证码方案由本 CR 在当前设计基线中取代，历史记录保留。
- 文档与代码一致性：当前没有 Rag2OKF 业务代码、Flyway 迁移或生产数据，只有 SDD 与原型资产。
- 技术设计与当前实现一致性：不适用，尚未实现。
- 前置条件：账号密码注册方式已由用户确认选择方案 A；目标初始表尚不存在，因此不存在存量密码或远程账号迁移。
- UI 设计确认：登录和注册原型已按账号密码更新并重新渲染；T002 UI Gate 仍等待用户确认。

## 4. 影响范围

- 需求影响：登录、注册、账号归属、密码安全、会话失败、权限和敏感数据规则调整。
- 技术设计影响：架构、依赖、HTTP 契约、账号模型、Redis key、会话、CSRF、异常、安全、验证与风险全部调整。
- 代码影响：未来使用 LocalAccountRepository、PasswordHasher、AuthenticationRateLimiter、Rag2OkfStpInterface 和 SaTokenAuthTemplate；删除远程身份/验证码 adapter 规划。
- 测试影响：新增密码摘要、账号唯一、暴力尝试、Cookie/CSRF、默认权限替换、踢下线和远程认证残留检查。
- 接口/契约影响：保留 `POST /auth/session`、`POST /auth/registration`、`DELETE /auth/session`；删除规划中的 `/auth/registration-code` 和 `/auth/session/refresh`；注册请求体改为账号密码。
- 权限/安全影响：高。Rag2OKF 成为账号与密码事实来源，必须负责密码摘要、认证频控、账号枚举防护、会话 Cookie 与 CSRF。
- 兼容/回滚影响：当前未实现，无存量兼容风险；实施后不得以重新启用 Auth Dubbo 或短信验证码作为隐式降级。

## 5. 需求与 AC 变化

- 新增 AC：无。
- 变更 AC：AC-026、AC-027、AC-028、AC-029、AC-031、AC-032。
- 删除 AC：无。
- REQ/AC 映射调整：REQ-001、REQ-014、REQ-016 及 BR-013～BR-016 改为本地账号密码和 Sa-Token 会话语义；知识库业务 AC-001～AC-025、AC-030 保持不变。

## 6. 技术设计影响

- API/RPC/消息影响：
  - 不再调用 Auth Service API、Communication API 或认证 Dubbo；
  - `/auth/session` 接收 username/password/rememberMe；
  - `/auth/registration` 接收 username/password/confirmPassword/displayName/termsAccepted；
  - 删除 registration-code 与远程 refresh 契约；
  - logout 直接结束当前 Sa-Token 会话。
- 领域对象/业务规则影响：`kb_user` 从“远程账号映射”升级为本地登录账号；username 全局唯一，password_hash 由专用 PasswordHasher 写入；Workspace Member 仍是知识库业务角色来源。
- 状态流转影响：注册事务提交后建立 Sa-Token 会话；登录时密码校验成功后建立会话；会话建立失败不得制造半成账号，账号禁用时应踢下线。
- 事务/一致性影响：本地账号、PERSONAL Workspace 和 ADMIN Member 在单个 MySQL 事务内创建；会话在事务提交后建立。
- 工具包/依赖影响：新增/保留 `fons4cloud-auth-satoken`、Spring Security Crypto、Common Cache；移除 Auth Service API、Infrastructure Communication API 和认证 Dubbo 依赖。

## 7. 数据结构与 DDL 影响

- 是否涉及持久化结构变更：是，调整尚未实施的初始 MySQL `kb_user` 目标字段与 Redis 认证 key。
- SQL 当前结构快照：无；目标项目没有已实施结构，未来快照仍为 `.specify/sql/knowledge_engine/knowledge_document_lifecycle.sql`。
- SQL DDL 动作：无存量 ALTER；现有 T003 直接生成 CR-004 后的 V1 初始迁移，T004/T005 继续负责验证与快照。
- DDL 分组：MySQL 账号字段仍属于 knowledge_engine 文档生命周期初始模型；Redis 会话/频控属于同一服务运行结构，不写入 SQL。
- 存量表原始 DDL：无，仓库不存在 Rag2OKF Flyway 迁移或已部署表。
- 执行型变更 DDL：不适用，不存在已实施结构；设计阶段不生成初始迁移。
- DDL 执行方式：未来由 T003 生成 Flyway V1，T004 仅在隔离测试环境执行。
- DDL 执行确认：未进入实现，不执行。

目标结构变化：

| 对象 | 原规划 | CR-004 目标 | 证据状态 |
| --- | --- | --- | --- |
| `kb_user` | `auth_account_id`、`username_snapshot`，不保存密码 | `username` 唯一、`password_hash`、`password_changed_at`，本地账号为事实来源 | 用户确认 L3；字段详设见技术设计 §4.6 |
| Sa-Token Redis key | 自定义 ServerSessionStore + 远程 token | 使用 Sa-Token Redis DAO 原生 key | 模块源码 L2；配置待实现 |
| 登录频控 | 无或依赖远程 Auth | username hash + IP hash 窗口 | 设计建议，待实现验证 |
| 注册频控 | phone hash/IP + 短信窗口 | username hash/IP 窗口，无短信 key | 用户决策 + 设计建议 |

## 8. 回归与回滚

- 回归风险：密码明文/摘要泄露、账号枚举、暴力尝试、Sa-Token 默认空权限实现误用、Cookie CSRF、注册半成数据和远程认证残留。
- 回归验证范围：依赖树、静态残留、密码摘要、登录/注册频控、统一错误、事务回滚、Sa-Token Redis 会话、Cookie 属性、CSRF、授权、踢下线和三主题 E2E。
- 回滚方案：实现前可恢复 CR-003 设计；实现后只能回滚到上一已审核构建和数据库备份，不允许临时启用远程 Auth、短信验证码、明文密码或双重会话。
- S2 风险门禁：
  - password/confirmPassword/password_hash/Sa-Token 不进入日志、响应或前端持久化；
  - 密码摘要使用带随机盐的自描述算法格式；
  - 登录/注册统一错误、频控和账号枚举防护通过；
  - Cookie HttpOnly/Secure/SameSite 与 CSRF 门禁通过；
  - 生产使用业务 Rag2OkfStpInterface，不使用默认空权限实现；
  - 账号/Workspace/Member 单事务，注册失败无半成数据；
  - 远程 Auth/Communication/SMS/双重会话残留为零。

## 9. 长期知识影响

- 是否产生长期知识影响：是。
- 影响类型：业务规则、技术方案、数据结构、接口契约、安全规则。
- 影响说明：Rag2OKF 成为独立账号与密码认证主体，Fons4Cloud 只以 `fons4cloud-auth-satoken` 库形式提供会话能力，不再调用统一认证服务。
- 处理边界：知识沉淀由 `fons4ai-knowledge-summary` 在用户显式触发后处理，本 CR 不生成知识同步任务或知识汇总交接任务。

## 10. 文档更新

- `知识库文档生命周期-需求说明书.md`：升级至 V1.3.0，重写 Q6/Q8、REQ-001/014/016、BR-013～016、AC-026～032 的认证相关语义。
- `知识库文档生命周期-技术设计说明书.md`：更新架构、API、字段映射、MySQL/Redis 结构、核心逻辑、安全、决策、页面、验证和风险。
- `知识库文档生命周期-任务规划.md`：重构 T001～T006/T016/T020～T022/T025～T027，追加 T028。
- `design/知识库文档生命周期-UI设计.md`：注册改为账号密码并删除短信/验证码交互。
- `docs/product-design/prototypes/v4/`：更新登录与注册页并重新生成亮暗主题评审图。

## 11. 增量任务

| 任务 ID | 任务标题 | AC | 追加位置 |
| --- | --- | --- | --- |
| T028 | 关闭远程认证残留并验证 Sa-Token 安全门禁 | AC-026～AC-029、AC-031、AC-032 | `知识库文档生命周期-任务规划.md` |

### 11.1 任务规划追加片段

- [ ] T028 关闭远程认证残留并验证 Sa-Token 安全门禁
  - 通俗解释: 完成后知识库只依赖自身账号密码和 Sa-Token 会话，不再暗中调用外部认证或短信服务，并具备可验证的密码与 Cookie 安全边界。
  - AC: AC-026、AC-027、AC-028、AC-029、AC-031、AC-032
  - 来源: CR-004；技术设计说明书 §3.1～§3.3、§4.4～§4.6、§8.2、D-009
  - Files: `fons4ai-rag2okf-service/pom.xml`; `fons4ai-rag2okf-service/src/main/resources/application.yml`; `fons4ai-rag2okf-service/src/main/java/com/fons/cloud/ai/rag2okf/infrastructure/security/`; `fons4ai-rag2okf-service/src/test/java/com/fons/cloud/ai/rag2okf/security/SaTokenSecurityGateIT.java`; `fons4ai-rag2okf-service/src/test/java/com/fons/cloud/ai/rag2okf/security/RemoteAuthResidueTest.java`
  - Depends: T006、T016、T021、T025、T027
  - Verification: 依赖树与静态扫描确认无 Auth Service API、Communication API、认证 Dubbo adapter、registration-code/refresh endpoint、clientSecret、手机号/验证码字段和双重会话；验证 BCrypt 摘要随机盐、错误统一、频控、Cookie HttpOnly/Secure/SameSite、CSRF、会话过期、注销和禁用账号踢下线。
  - Quality: 只使用 `fons4cloud-auth-satoken` 对外能力，不绕过封装直接散落 `StpUtil`；PasswordHasher、StpInterface 和安全配置集中；默认空权限 Bean 不得在生产生效。
  - Done: 远程认证与短信残留为零，密码、会话、CSRF、频控、权限和敏感信息门禁全部有自动化证据。

## 11.2 关键证据

- 用户明确确认不采用 Fons4Cloud 统一认证，改用独立 Sa-Token 能力和账号密码登录。
- 用户确认方案 A：保留注册页并改为账号密码注册。
- `fons4cloud-auth-satoken` POM 说明其面向不接入认证服务与网关的单点应用，并通过 Common Cache Redis 持久化会话。
- `SaTokenAuthTemplate` 只封装 login/logout/checkLogin/token/kickout，不提供账号存储或密码校验。
- `DefaultStpInterfaceImpl` 默认返回空权限/角色，Rag2OKF 必须提供业务实现。
- 当前没有 Rag2OKF 业务代码、Flyway DDL 或生产数据，V1 初始结构可以直接采用 CR-004 目标。

## 12. 实现确认门禁

- 状态：等待用户确认。
- 当前只完成认证架构、SDD 和原型增量设计，不执行 T001～T028，不生成业务代码或 DDL。
- T002 UI Gate 仍等待用户确认账号密码登录、注册和其余 V4 页面。
- 规划产物不等于实现授权。
- 确认执行后默认执行全部未完成任务；如需指定范围，请回复：执行 T001,T002。
