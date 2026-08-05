# CR-007 移除 Flyway 与手工初始化 SQL 调整

> 功能标识：`knowledge-base-document-lifecycle`
> 变更类型：重构
> SDD 等级：`S2`
> 文档状态：正式
> 创建日期：2026-07-31

## 1. 变更摘要

- 变更一句话说明：移除 Flyway 数据结构版本管理，改为交付一份由开发者、DBA 或部署流程手工执行的完整 MySQL 初始化 SQL。
- 本次变更结论：初始化 SQL 固定放在 `fons4ai-rag2okf-service/sql/init-schema.sql`；应用启动不创建或修改表；隔离 MySQL 测试通过 JDBC 执行同一份 SQL，不建立 `flyway_schema_history`。
- 是否建议新建 feature：否。数据库目标结构、业务规则、API 和 AC 均不改变，只调整 DDL 的交付、执行和验证方式。

## 2. 变更原因

- 用户诉求：项目不需要数据结构版本管理工具，并确认采用方案 A——保留完整初始化 SQL，部署时手工执行，测试通过 JDBC 加载。
- 业务或技术背景：当前为尚未发布的新系统，没有生产数据库、存量表或已执行的 Flyway 版本；Flyway 只存在于 T003 的候选实现中，移除成本和兼容风险最低。
- 不变更的影响：继续保留 Flyway 会引入不需要的运行依赖、启动期数据库写入和版本历史表，并让应用承担本应属于部署流程的结构管理职责。

## 3. 当前状态检查

- 任务规划未完成项：T003～T030 中除已完成 T001/T002 外均未关闭；T003 已形成候选 SQL、实体和测试，但因隔离 MySQL 测试未执行而保持未完成。本 CR 追加 T031，并把它作为 T003 的前置任务。
- 历史 CR 未完成项：CR-001～CR-006 的命名、锁、认证、邮箱账号和独立打包决策继续有效，与本次变更不冲突。
- 文档与代码一致性：存在差异。当前后端 POM、`db/migration/V1__init_knowledge_engine.sql` 和测试仍引用 Flyway，等待 T031 实现时统一调整。
- 技术设计与当前实现一致性：当前设计仍规定 Flyway 版本化迁移，与用户最新决策不一致；本 CR 同步修正技术设计和任务规划。
- 前置条件：设计阶段条件已满足；实现仍需用户明确授权。隔离 MySQL 测试可以在具备容器环境后执行，不作为需求和设计前置。

## 4. 影响范围

- 需求影响：无。知识库、文档、解析、分块、发布和认证行为不变。
- 技术设计影响：更新 §4.5、§4.6、§4.10、D-012、依赖清单和 DDL 质量门禁。
- 代码影响：实现阶段移除后端 POM 中的 Flyway 依赖，移动初始化 SQL，并调整结构契约测试和 MySQL 集成测试。
- 测试影响：结构测试不再调用 Flyway，改为使用 JDBC 按脚本顺序执行 `sql/init-schema.sql`；继续验证 11 张表、索引、约束和 Mapper 基础读写。
- 接口/契约影响：无。
- 权限/安全影响：无新增权限影响；应用账号不需要生产 DDL 权限，部署账号和应用账号应分离。
- 兼容/回滚影响：当前没有已发布数据库或 Flyway 历史，无存量兼容与数据回填问题；若未来已部署环境发生结构变化，需单独 CR 和人工审核 SQL，不得依赖修改已执行过的初始化脚本完成原地升级。

## 5. 需求与 AC 变化

- 新增 AC：无。
- 变更 AC：无。
- 删除 AC：无。
- REQ/AC 映射调整：无；T031 复用 AC-003、AC-009、AC-023 验证知识库结构、文档指针和服务交付边界。

## 6. 技术设计影响

- API/RPC/消息影响：不适用，外部契约不变。
- 领域对象/业务规则影响：不适用，11 张表及其领域语义不变。
- 状态流转影响：不适用，文档和任务状态机不变。
- 事务/一致性影响：业务事务不变；应用启动不得执行 DDL，初始化结构由部署前手工步骤完成。
- 工具包/依赖影响：移除 `flyway-core` 与 `flyway-mysql`；保留 MySQL JDBC 和 Testcontainers，测试自行执行同一份初始化 SQL。

## 7. 数据结构与 DDL 影响

- 是否涉及持久化结构变更：否。目标仍为 §4.6 定义的 11 张表、字段、索引和约束。
- SQL 当前结构快照：实现阶段 T005 生成 `.specify/sql/knowledge_engine/knowledge_document_lifecycle.sql`。
- SQL DDL 动作：无结构动作；只调整完整初始化 DDL 的文件位置和执行责任。
- DDL 分组：同一 MySQL 服务下的知识库文档生命周期结构合并为一份完整初始化 SQL。
- 存量表原始 DDL：无，当前为未部署新系统。
- 执行型变更 DDL：`fons4ai-rag2okf-service/sql/init-schema.sql`。
- DDL 执行方式：用户/DBA/部署流程手动执行；应用启动不自动执行。
- DDL 执行确认：隔离 MySQL 由自动化测试通过 JDBC 执行并只读验证；真实环境由部署责任方记录手工执行结果，Agent 不直接执行生产 DDL。

## 8. 回归与回滚

- 回归风险：测试可能误用另一份 SQL，或 Spring Boot 配置重新开启自动初始化，造成双重事实来源或启动期 DDL。
- 回归验证范围：依赖树和源码静态扫描无 Flyway、`flyway_schema_history`、`db/migration`；隔离 MySQL 使用交付 SQL 创建 11 张表并通过结构与 Mapper 测试；应用启动链路不存在自动建表配置。
- 回滚方案：当前结构未部署，无数据库回滚动作；若实现变更本身需撤销，可恢复 Flyway 依赖和旧路径后重新评审，但不得对已部署数据库做隐式降级。
- S2 风险门禁：初始化 SQL、隔离 MySQL 实际结构和 SQL 当前结构快照必须一致；应用运行身份不承担 DDL；后续结构变化必须单独评审。

## 9. 长期知识影响

- 是否产生长期知识影响：是。
- 影响类型：技术方案、数据结构治理规则。
- 影响说明：Rag2OKF 不采用数据库版本管理工具；完整初始化 SQL 是新环境建库的单一事实来源，应用不负责结构初始化，测试复用同一脚本。
- 处理边界：知识沉淀由 `fons4ai-knowledge-summary` 在用户显式触发后处理，本 CR 不生成知识同步任务或知识汇总交接任务。

## 10. 文档更新

- `知识库文档生命周期-需求说明书.md`：不更新，业务需求和 AC 不变。
- `知识库文档生命周期-技术设计说明书.md`：更新 §4.5、§4.6、§4.10、§9、依赖清单和版本修订记录。
- `知识库文档生命周期-任务规划.md`：修正 T003～T005、依赖图与 DDL 门禁，追加任务 T031。
- 变更记录：已追加。

## 11. 增量任务

| 任务 ID | 任务标题 | AC | 追加位置 |
| --- | --- | --- | --- |
| T031 | 移除 Flyway 并切换为手工初始化 SQL | AC-003、AC-009、AC-023 | `知识库文档生命周期-任务规划.md` |

### 11.1 任务规划追加片段

- [ ] T031 移除 Flyway 并切换为手工初始化 SQL
  - 通俗解释: 完成后新环境由部署人员手工执行一份完整初始化 SQL，应用启动不改表，自动化测试会验证这份 SQL 确实能创建知识库所需结构。
  - AC: AC-003、AC-009、AC-023
  - 来源: CR-007；技术设计说明书 §4.6、§4.10、D-012
  - Files: `fons4ai-rag2okf-service/pom.xml`; `fons4ai-rag2okf-service/sql/init-schema.sql`; `fons4ai-rag2okf-service/src/main/resources/db/migration/V1__init_knowledge_engine.sql`; `fons4ai-rag2okf-service/src/test/java/com/fons/cloud/ai/rag2okf/infrastructure/persistence/KnowledgeSchemaContractTest.java`; `fons4ai-rag2okf-service/src/test/java/com/fons/cloud/ai/rag2okf/infrastructure/persistence/KnowledgePersistenceMySqlIT.java`
  - Depends: T001
  - Verification: Maven 依赖树和源码扫描不含 Flyway、`flyway_schema_history` 与 `db/migration`；结构契约测试读取 `sql/init-schema.sql`；隔离 MySQL 测试通过 JDBC 执行同一脚本并验证 11 张表、关键约束和 Mapper CRUD；确认应用启动链路不执行 DDL。
  - Quality: 初始化 SQL 只有一个事实来源，不复制为 `schema.sql`；应用与生产运行账号不承担 DDL；测试执行器只负责分隔和执行 SQL，不实现自定义版本管理；遵循 DDD-lite，DDL 交付位于项目级 `sql/`，持久化适配器仍位于 infrastructure。
  - Done: POM 无 Flyway，初始化 SQL 已移动，测试改为 JDBC 复用同一脚本，不生成 Flyway 历史表，应用启动无结构变更行为。

### 11.2 DDL 任务片段

不适用。本次不改变持久化结构，只改变既有新建结构脚本的交付位置与执行方式。

## 12. 实现确认门禁

- 状态：等待用户确认。
- 当前只完成变更规划和 SDD 文档调整，不移除依赖、不移动 SQL、不修改测试。
- T003 保持未完成；T031 完成并通过验证后，T003 才能继续关闭。
- 规划产物不等于实现授权。
- 生成本 CR 和增量任务后必须暂停，等待用户确认后才能进入业务代码实现。
- 用户确认执行且未指定任务 ID 时，默认执行全部未完成任务。
- 用户指定任务 ID 时，例如 `执行 T031,T003`，只执行指定任务及其尚未完成的必要依赖。
- 确认执行后默认执行全部未完成任务；如需指定范围，请回复：执行 T001,T002。
