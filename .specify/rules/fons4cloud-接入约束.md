# fons4cloud 接入约束

> 适用范围：下游业务仓接入 fons4cloud 框架
> 规则状态：已建立
> 更新日期：2026-08-03
> 依据来源：fons4cloud 仓源码、`.specify/rules/代码编写规范.md`、`AGENTS.md`

## 适用说明

- 本规则供下游业务仓接入 fons4cloud 框架时使用，约束"框架能力复用与禁止造轮子"，不替代下游业务仓自身的代码编写规范。
- 下游业务仓引入 fons4cloud 依赖后，将本文件 copy 到自身 `.specify/rules/fons4cloud-接入约束.md`；fons4ai-sdd 流程（`fons4ai-sdd-design`、`fons4ai-sdd-implement` 等）会自动加载 `.specify/rules/` 下的规则文件。
- 本规则中所有类名与包路径均来自 fons4cloud 仓源码事实，下游使用时以实际引入的 fons4cloud 版本源码为准；版本升级时从 fons4cloud 仓同步更新本文件。

## 核心原则

- 框架已提供的能力必须复用，禁止平行实现。
- 鉴权、网关、Token、锁、MQ、限流、文件等关键链路不得绕过框架机制（按业务实际接入的模块适用）。
- 引入新依赖前确认与框架技术栈不冲突（框架栈：JDK 21、Spring Boot 3.5.x、Spring Cloud 2025.x、Spring Cloud Alibaba 2025.x）。
- 不得编造框架接口、字段或配置；不确定时先读 fons4cloud 源码或请求确认。

## 必须复用的框架能力

### 统一返回

- HTTP/RPC 返回必须使用 `com.fons.cloud.common.result.R`，禁止新增与 `R` 并行的返回模型。
- 框架层通用错误码复用 `com.fons.cloud.common.result.ResultCode`（如 `SUCCESS`、`PARAMS_ERROR`、`FAILED` 等）。
- 业务允许自定义错误码，但必须遵循 `com.fons.cloud.common.result.Result` 接口约定（实现 `getCode()`/`getMessage()`），并按 `ResultCode` 的格式规范命名：`前缀 + 错误类型(0-9) + 序号(00001-99999)`；不散落裸字符串或魔法值。

### 异常体系

- 框架层提供三类异常，业务按语义选用：
  - `com.fons.cloud.common.base.exception.BizException`：可预期业务失败。
  - `com.fons.cloud.common.base.exception.BusinessRuntimeException`：业务运行时异常。
  - `com.fons.cloud.common.base.exception.SystemIntervalException`：系统内部异常。
- 禁止裸 `throw new RuntimeException` 或字符串错误散落；业务可在框架异常之上按领域扩展自己的异常类型。
- 系统异常保留 cause，不吞掉影响一致性的失败。
 
### 对象转换

- 5 个以上字段映射或同类字段重复搬运时，优先使用 `com.fons.cloud.common.base.converter.CommonConverter` 或 MapStruct，不手写搬运逻辑。
- 不使用转换器必须在实施报告中说明原因。

### 分布式锁

- 分布式锁使用 `com.fons.cloud.lock.annotation.DistributeLock` 注解或 `com.fons.cloud.lock.service.LockService`，禁止自实现 Redis 锁。

### 限流

- 限流使用 `com.fons.cloud.limiter.api.Limiter`，禁止自实现限流算法绕过框架能力。

### 文件存储

- 文件上传/存储使用 `com.fons.cloud.file.api.FileService`，禁止在业务模块自实现 OSS/MinIO 直连。

### 工具类

- 通用能力（JSON、金额、断言、ID、并发、日期、反射等）优先使用 hutool 或框架层 `com.fons.cloud.util.*` 提供的工具类，禁止重复造轮子。

## 包结构模板

- 下游业务接入 fons4cloud 时，推荐采用以下 DDD-lite 基础包结构作为**基础模板**；业务可按自身领域复杂度在此基础上扩展，不强制包名完全一致，但分层骨架（接口层→应用层→领域层→基础设施层）和依赖方向必须保持。

  ```
  com.fons.cloud.{业务标识}
  ├── controller/          接口层：HTTP/RPC 入口，仅做协议适配和参数校验，不含业务编排
  ├── application/         应用服务层：用例编排、事务边界、外部协作（可含 impl/ 子包）
  ├── common/              通用层：跨层共享模型
  │   ├── constants/      常量、业务错误码（implements Result）、状态枚举
  │   ├── exception/      业务异常
  │   ├── request/        请求对象
  │   ├── response/       响应对象
  │   ├── dto/            数据传输对象
  │   └── vo/             响应/视图对象
  ├── domain/              领域层
  │   ├── entity/         领域实体（不依赖 MyBatis/MinIO/ES/Spring AI 等基础设施类型）
  │   ├── mapper/         MyBatis Mapper（持久化适配）
  │   └── service/       领域服务（封装领域规则和状态变化）
  ├── infrastructure/      基础设施层：配置、转换器、外部适配
  │   ├── config/         AutoConfiguration、Properties
  │   ├── converter/      MapStruct 转换器（uses = CommonConverter.class）
  │   └── {业务特定实现}/  按需扩展，如 prompt/、adapter/、client/ 等
  └── {Main}.java          启动类
  ```

- 分层依赖方向单向：`controller → application → domain ← infrastructure`；领域实体不依赖基础设施类型。
- 业务错误码放 `common/constants/`，`implements com.fons.cloud.common.result.Result`，按 `ResultCode` 格式规范命名（前缀 + 错误类型 + 序号）。
- 对象转换器放 `infrastructure/converter/`，用 MapStruct 并 `uses = CommonConverter.class` 复用框架转换能力。
- 领域实体不得依赖 MyBatis、MinIO、Elasticsearch、Spring AI 等基础设施类型，这些依赖只出现在 `infrastructure/` 或 `domain/mapper/` 下。
- **本模板为基础骨架，非强制包名清单**：业务领域复杂时可扩展更多子包（如 `domain/event/`、`domain/repository/`、`infrastructure/adapter/`），简单业务也可裁剪合并；只要保持分层依赖方向和框架能力复用约束即可，不要求与模板逐字一致。

## 鉴权与网关硬约束（仅适用于接入 fons4cloud-auth-core 的业务）

- fons4cloud 提供多套鉴权方案：`fons4cloud-auth-core`、`fons4cloud-auth-satoken`（Sa-Token）、`fons4cloud-auth-spring-security`。本节约束仅适用于接入 `fons4cloud-auth-core` 的业务；采用其他方案的业务遵循对应方案约定，不强制套用本节。
- 网关与下游之间的认证用户传递走 `com.fons.cloud.auth.common.AuthUserHeaderConstants.AUTH_USER` 头（常量值 `"auth_user"`）；客户端不得伪造该头。
- 获取当前登录用户使用 `com.fons.cloud.auth.utils.AuthUtils`，不自行解析 Header 或重建用户上下文。
- 权限资源点使用 `com.fons.cloud.auth.annotation.AuthenticationResource` 声明，预认证使用 `com.fons.cloud.auth.annotation.PreAuthentication`，不重复实现鉴权拦截。
- Token 生成、刷新、吊销、内省和权限校验由所选鉴权方案负责，网关和业务模块不重复实现。
- 鉴权 AutoConfiguration 由所选方案的配置类装配（`fons4cloud-auth-core` 为 `com.fons.cloud.auth.autoconfigure.AuthConfiguration`），业务模块不重复声明。

## 数据访问与事务

- 数据访问优先使用 `fons4cloud-common-db` 提供的 MyBatis-Plus 配置、公共 Mapper 和自动填充能力。
- 事务边界放在应用服务；跨数据库、MQ、Redis、外部服务的一致性必须说明补偿或失败处理。
- 更新、删除、批量操作必须具备条件约束，不得绕过防全表更新与删除保护。

## 安全与日志

- 日志不得输出完整请求体、token、密码、clientSecret、身份证、手机号、邮箱、连接串等敏感数据。
- 鉴权失败、无权限、非法请求、限流等安全事件保持统一错误结构，不返回堆栈或内部实现细节。
- 敏感数据加密和脱敏遵循框架既有 TypeHandler 与脱敏模式，不得绕过。

## 禁止事项

- 禁止新增与 `R`、`Result`、`BizException`、`CommonConverter` 并行的重复体系。
- 禁止裸 `throw new RuntimeException` 或字符串错误散落。
- 禁止绕过 `@DistributeLock`/`LockService`、`Limiter`、`StreamService`、`FileService` 自实现锁、限流、消息、文件能力。
- 接入 `fons4cloud-auth-core` 的业务，禁止绕过 `AuthUtils`、`@AuthenticationResource` 自实现鉴权与权限校验。
- 禁止客户端伪造 `AUTH_USER` 头或绕过网关注入用户上下文。
- 禁止引入与框架技术栈冲突的重量级依赖。
- 禁止在源码、配置、测试、日志或文档中提交真实密钥、token、账号、连接串、身份证、手机号、邮箱等敏感信息。

## 依赖引入

- 通过 `fons4cloud-starter`（dubbo/nacos）或 `fons4cloud-common-*` 按需引入对应能力。
- 鉴权能力按业务选型引入：`fons4cloud-auth-core`、`fons4cloud-auth-satoken`（Sa-Token）或 `fons4cloud-auth-spring-security`。
- 网关能力由 `fons4cloud-gateway` 提供，业务模块不重复实现网关逻辑。
- MQ 能力通过 `fons4cloud-mq` 模块按中间件（kafka/rabbitmq/rocketmq）引入。

## 下游使用说明

- 引入 fons4cloud 依赖后，将本文件 copy 到业务仓 `.specify/rules/fons4cloud-接入约束.md`。
- fons4ai-sdd 的 `fons4ai-sdd-design`、`fons4ai-sdd-implement`、`fons4ai-sdd-change` 会自动加载 `.specify/rules/` 下规则文件作为项目规则和 Evidence 来源。
- 业务仓自身的 `.specify/rules/代码编写规范.md` 仍由 `fons4ai-generate-project-rules` 基于业务仓自身事实生成，本文件不替代它，仅补充框架级硬约束。
- fons4cloud 版本升级时，从 fons4cloud 仓同步更新本文件，避免规则与框架实现漂移。
