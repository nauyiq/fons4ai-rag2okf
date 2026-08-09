# CR-001 模型设置改为 Ragflow 式「提供商 + 模型」双栏交互

> 功能标识：`frontend-interaction-redesign`
> 变更类型：重构 + 契约变更 + 数据结构（枚举）变更
> SDD 等级：`S2`
> 文档状态：正式
> 创建日期：2026-08-08

## 1. 变更摘要

- 变更一句话说明：**撤销原 REQ-004「模型配置合并为单一概念」的设计方向**，改回原本 `kb_model_connection`（模型提供商）+ `kb_model_profile`（模型）两张表的两层结构；前端模型设置页面按 **Ragflow 交互范式**整体重写为左右双栏（左侧：默认模型配置 + 按提供商分组的已添加模型；右侧：模型市场「提供商模板」）。
- 本次变更结论：
  - 后端 DDL **不需要执行合并回滚**（原 T014 的 `kb_model_config` 建表从未执行，[init-schema.sql](file:///d:/hongqy/code/cloud/fons4ai-rag2okf/fons4ai-rag2okf-service/sql/init-schema.sql#L26-L93) 仍为两张表结构 ✔️）
  - 后端 Model API 仍保持两步式（`/model-connections` + `/model-profiles`，[ModelConfigurationController.java](file:///d:/hongqy/code/cloud/fons4ai-rag2okf/fons4ai-rag2okf-service/src/main/java/com/fons/cloud/ai/rag2okf/controller/ModelConfigurationController.java#L36-L80) 已是此结构 ✔️）；原 T006 前端写入的合并单步契约（`/model-configs`）在前端移除并改回使用两步式 API。
  - 默认模型配置**不需要新增表**：直接写入 `kb_user.preference_json` 字段（已存在，[UserProfileController.java](file:///d:/hongqy/code/cloud/fons4ai-rag2okf/fons4ai-rag2okf-service/src/main/java/com/fons/cloud/ai/rag2okf/controller/UserProfileController.java#L48-L55) 的 `PATCH /users/me` 已支持读写 `preferenceJson` ✔️）。
  - 模型类型枚举一步到位扩展为 7 值：`LLM / EMBEDDING / RERANK / TTS / ASR / VLM / OCR`（存量 `CHAT` 显示兼容为 `LLM`）。
  - 顶部账号菜单的「设置」入口点击后直接进入模型设置 Tab，与 Ragflow 行为一致。
- 是否建议新建 feature：否；本次属同一「前端交互体系重设计」功能内的交互方向调整，未影响的 AC（知识库、文档工作台/详情、顶部导航、主题、演示数据切换、文档删除管理等）超过 70%。

## 2. 变更原因

- 用户诉求：用户参考了 Ragflow 的模型设置交互方式（左右双栏：默认模型 + 已添加模型分组卡 / 右侧市场），希望 Rag2OKF 采用相同范式；同时明确要求「还原原本两张表的配置」，不使用合并的单表概念。
  - 新增模型提供商只需要 **3 字段**（URL、API Key、名称）。
  - 左侧上：默认模型配置（使用个人偏好字段存储）；左侧下：每个提供商下添加了哪些模型。
  - 右上角点击直接进入设置页面（模型提供商 Tab 默认），交互逻辑与 Ragflow 一致。
- 业务或技术背景：
  - T006 前端写入的「合并 API 契约」后端未跟进实现，后端仍保留两张表和两步式 API（L2 证据，消除了契约不匹配风险的大部分）。
  - T011 的「合并表单」前端代码已完成并通过测试，但用户重新评估后认为 Ragflow 的「先选提供商 → 再挂模型」的两层心智模型更贴合实际模型接入场景（1 个 API Key 可挂多个模型，如同一 DeepSeek 凭证同时用 deepseek-chat + deepseek-reasoner）。
- 不变更的影响：
  - 合并单表意味着一个 API Key 只能绑定一个模型名（一对一），用户接入同一厂商多个模型需要重复填 Base URL/API Key，体验差。
  - 「默认模型」概念缺失意味着每个知识库都要手动绑定模型，新用户入门门槛高，与同类产品范式不匹配，造成用户学习成本上升。

## 3. 当前状态检查

- 任务规划未完成项：
  - **T014/T015/T020/T021**（模型表合并 DDL/后端 API/迁移回滚/字段安全）：**停止执行，方向撤销**，由本 CR 的 T032 任务重新承接「枚举扩展 + 目录接口 + binding 白名单」的后端工作。
  - **T013/T017~T019**：不停止，在 T025~T031 完成后重新跑一次交互验证。
  - 其余 T001~T005、T007~T010、T012、T016、T022~T024：保持既定方向不变。
- 历史 CR 未完成项：无（changes 目录空）。
- 文档与代码一致性：存在差异，需说明：
  - [models.ts](file:///d:/hongqy/code/cloud/fons4ai-rag2okf/fons4ai-rag2okf-ui/src/api/models.ts) 的合并契约 `ModelConfig / SaveModelConfigInput` 和 `createModelConfig / etc.` 与后端实际 Controller 的 `/model-connections + /model-profiles` 不一致（前端合并设计未被后端实现）。由 T025 任务解决此不一致。
- 技术设计与当前实现一致性：
  - §3.1 的模型设置合并 API 设计与后端代码真实存在的两步式 API 不一致（后端未跟进合并）。T025 + T032 解决此差异。
- 前置阻塞：无。关键确认项（撤销策略 A、7 类型范围）用户均已明确答复；DDL / API / 偏好存储的 L2/L3 证据齐备。

## 4. 影响范围

- 需求影响：有
  - **REQ-004 撤销并重写**（原：合并为单一模型概念 → 新：还原两张表 + Ragflow 式双栏 + 默认模型 + 模型市场）
  - **BR-004 撤销并重写**（原：不再区分连接与档案 → 新：明确区分 Provider 连接 + 模型档案两层）
  - **REQ-011 补充**（顶部账号菜单的设置入口点击直达模型设置 Tab 默认页）
  - 新增 BR-015~BR-017（默认模型偏好存储、7 类型枚举、模型市场级联选择）
  - **AC-008/AC-009 替换**；新增 AC-026~AC-032（见 §5）
- 技术设计影响：有
  - §3.1「模型设置 API 合并」**整节替换**为 3 个 API 族：两步式 API + 模型目录 API + 默认模型偏好 API
  - §3.3 的个人偏好 PATCH /users/me 补充 `preferenceJson.defaultModels` 结构
  - §4.2 字段映射契约**整节替换**为两张表现有字段的兼容映射（7 类型、parameters_json 拆独立字段保留原档案）；新增 §4.8「默认模型偏好 JSON 结构」
- 代码影响：有，涉及文件
  - 前端 `fons4ai-rag2okf-ui`：
    - `src/api/models.ts`：撤销 ModelConfig；恢复两步式 API（connection + profile）；新增模型目录 API + 默认模型偏好读写 API（通过 auth.ts / users-me 扩展）
    - `src/api/mock/models.ts`：同步恢复两张表 mock，新增目录 + 偏好 mock
    - `src/composables/useModelForm.ts`：拆为 `useModelProviderForm`（3 字段表单 + 校验）+ `useModelProfileForm`（级联模型名选择 + 高级参数）；新增 `useModelCatalog`（搜索/类型筛选）；新增 `useDefaultModels`（preferenceJson 读写）
    - `src/views/settings/ModelSettingsTab.vue`：**模板+脚本 80% 重写**为左右双栏 Ragflow 式骨架
    - `src/layouts/Rag2OkfAppShell.vue`：账号菜单「设置」点击默认导航到 `/settings?tab=model-providers`
    - 对应 `__tests__` 与 `e2e` 文件：更新用例匹配新交互
  - 后端 `fons4ai-rag2okf-service`：
    - 新增 `GET /model-catalog` 接口（Provider 元数据 + 旗下模型清单 + 类型统计）
    - `kb_model_profile.model_type`：应用层白名单扩展为 7 值；MySQL 表结构为 `VARCHAR(24)` 无需 DDL（若后续有 CHECK 约束再补）
    - `kb_model_binding.usage_type` 应用层白名单扩展为 7 值
    - 存量 `CHAT` 兼容处理：读取 API 时 `CHAT → LLM` 做别名映射，写入禁止使用旧 CHAT
- 测试影响：有
  - `views/settings/__tests__/ModelSettingsView.spec.ts`：重写用例（左右双栏渲染、市场点击添加、默认模型 7 下拉、级联选择、三字段提供商表单、分组卡片）
  - `api/__tests__/models.spec.ts`：两步式契约 + 目录/偏好契约用例替换
  - `composables/__tests__`：新增 4 个 composable 用例；移除旧 useModelForm 合并用例（或保留兼容层时保留）
  - T013 demo 集成重跑；T017/T018/T019 回归范围增加模型设置新交互
- 接口/契约影响：有
  - 撤销：前端内部使用的 `/model-configs`（单步 CRUD，后端从未实现）
  - 恢复并复用：`GET/POST/PATCH /model-connections` + `GET/POST/PATCH /model-profiles` + `POST /model-profiles/{key}/test`（后端代码已存在，前端重新对齐）
  - 新增：`GET /model-catalog?type=`（所有用户一致的模型市场目录；可缓存）
  - 复用不新增：`PATCH /users/me` 写入 `preferenceJson.defaultModels`；`GET /users/me` 读取 `preferenceJson`（后端已实现）
- 权限/安全影响：有，延续并强化
  - API Key 安全处理延续 T006/T011：保存后只显示掩码，编辑连接时只替换不回显；新增「替换 API Key」独立入口（从连接卡片的操作菜单触发）
  - SSRF 校验：新 /model-catalog 中的 Base URL 为模板默认值，实际提交仍走原有 SSRF 白名单校验
  - 模型目录：公开只读，不包含用户私有连接或凭证信息
- 兼容/回滚影响：有
  - 存量 CHAT 值兼容：读取时 `CHAT → LLM` 别名映射；写入校验禁止 `CHAT`（返回 400）
  - 知识库 `kb_model_binding` 的 usage_type 白名单：新增值但旧值（CHAT/EMBEDDING）在兼容映射下仍可生效（CHAT 应用层转 LLM）
  - 回滚策略：若新交互上线后发现严重缺陷，可暂时保留两张表 API，前端旧合并表单需要完整恢复（T006/T011 代码在 Git 历史中保留）。推荐在生产前先完整走 demo 集成（T013），避免上线后回滚。

## 5. 需求与 AC 变化

- 新增 AC：
  - **AC-026**（模型市场右侧栏）：Given 进入模型设置 Tab，when 查看右侧，then 出现搜索框 + 7 类型分类标签栏（All/LLM/Embedding/Rerank/TTS/ASR/VLM/OCR，各带数量）+ 提供商卡片网格；卡片含 Logo、名称、官方跳转↗、支持的模型类型标签；点击整张卡触发从该提供商新增。来源：CR-001。
  - **AC-027**（提供商新增三字段表单）：Given 用户从右侧市场点击某提供商卡或「＋ 添加提供商」，when 表单打开，then 只看到 3 个字段：提供商名称（display_name 自动预填 + 可改）、Base URL（模板预填 + 可改）、API Key（新增必填）；不再出现模型名称或高级参数。来源：CR-001。
  - **AC-028**（默认模型左侧顶部 + 偏好持久化）：Given 进入模型设置 Tab，when 查看左侧上方「设置默认模型」卡，then 看到 7 个下拉框（LLM/Embedding/Rerank/TTS/ASR/VLM/OCR），下拉选项 = 当前用户已添加的同类型模型；当用户保存时，写入 `PATCH /users/me` 的 `preferenceJson.defaultModels` 字段；当用户再次进入时，从 GET /users/me 的 preferenceJson 回显。来源：CR-001。
  - **AC-029**（提供商分组卡片展示旗下模型）：Given 用户已添加至少一个提供商连接且其下有模型档案，when 查看左侧下方，then 出现按 provider 分组的卡片；每张卡头部=提供商名称 + 操作（展示更多模型▼ / 删除连接）；卡体=该提供商下每条模型档案行，含模型名、7 类型 tag、状态 tag、最近测试；每行有行内操作菜单（编辑 / 测试 / 删除 / 替换 API Key 继承连接）。来源：CR-001。
  - **AC-030**（模型档案级联选择新增）：Given 用户在某提供商卡点击「展示更多模型▼ 添加」，when 弹出档案表单，then 先选择模型类型（7 选 1），模型名称 Select 根据 provider_code + 选中类型从 /model-catalog 过滤级联；不再允许手填模型名称。基础信息填完后高级参数折叠区按类型显示对应字段。来源：CR-001。
  - **AC-031**（7 类型枚举 + 存量 CHAT 兼容显示）：Given 任意模型档案创建/展示/绑定场景，when 涉及 model_type，then 合法值为 7 类；存量旧值 `CHAT` 读取时自动按 `LLM` 显示，但创建/编辑写入校验拒绝 `CHAT`。来源：CR-001。
  - **AC-032**（设置直达入口）：Given 用户在任意页面点击顶部账号菜单的「设置」入口，when 页面跳转，then 默认定位到 `/settings` 路由并激活「模型提供商」Tab（与 Ragflow 一致）。来源：CR-001。
- 变更 AC：
  - **AC-008（替换）**：原「一个中央弹窗填完所有字段不再区分连接与档案两步」→ **新：明确两步**——先建提供商连接（3 字段），再在该提供商下挂模型档案（级联选择名称+类型+高级参数）。新增仍使用统一的中央弹窗，但表单按步骤切换。
  - **AC-009（变更）**：原「高级参数合并语境」→ **新：高级参数仅在模型档案表单中出现**，按 7 类型动态显示：LLM/VLM 含 contextWindow + temperature；EMBEDDING 含 dimensions；RERANK/TTS/ASR/OCR 仅 timeout；各类型分组呈现与基础信息折叠区分。
- 删除 AC：无显式删除条目（AC-008/009 内容替换，编号保留用于映射溯源）
- REQ/AC 映射调整：
  - REQ-004 替换为新映射 → AC-026, AC-027, AC-028, AC-029, AC-030, AC-031
  - REQ-005（高级参数分组）→ 仍保留并映射到新 AC-009（模型档案语境）
  - 新增 REQ-017（7 类型枚举 + 级联模型目录）→ AC-031, AC-030
  - 新增 REQ-018（默认模型偏好存储在个人偏好字段）→ AC-028
  - 新增 REQ-019（设置入口直达模型 Tab）→ AC-032

## 6. 技术设计影响

- API/RPC/消息影响：
  - **废弃（前端内部停止使用）**：`GET/POST/PATCH/DELETE /model-configs`、`POST /model-configs/{key}/test`、`PATCH /model-configs/{key}/api-key`（后端从未实现，仅在前端 mock/models.ts 存在）
  - **恢复启用（已存在后端实现）**：
    - `GET /model-provider-templates` → 继续保留用于兼容，但右侧市场主要走 /model-catalog
    - `GET /model-connections`、`POST /model-connections`、`PATCH /model-connections/{connectionKey}`
    - `GET /model-profiles`、`POST /model-profiles`、`PATCH /model-profiles/{profileKey}`
    - `POST /model-profiles/{profileKey}/test`
    - 新增语义：`PATCH /model-connections/{connectionKey}/api-key` 独立替换密钥入口（与合并 API 的 replaceKey 等价；若后端已有的更新连接接口可直接提交新 api_key_ciphertext，则复用）
  - **新增**：
    - `GET /model-catalog?type=LLM|...` → 返回 `ModelCatalog { providers: CatalogProvider[], typeCounts: Record<ModelType, number> }`；全量可缓存在 localStorage 30 分钟
  - **复用不新增接口，但扩展 preferenceJson 结构**：
    - `GET /users/me` → 读取 `preferenceJson`，客户端解析 `defaultModels` 字段
    - `PATCH /users/me` → 提交时只改动 `preferenceJson.defaultModels` 子节点，保留其他偏好（如后续出现的排序/语言等）不变；合并方式：后端先解析 JSON，局部更新，再整体写回
- 领域对象/业务规则影响：
  - 模型域恢复两层：
    - **ModelProviderConnection（模型提供商）**：1 owner → 多条连接；字段 = display_name + provider_code + provider_name + base_url + api_key_* + status + last_test_*
    - **ModelProfile（模型）**：1 连接 → 多条档案；字段 = connection_id + model_type（7 值） + model_name + dimensions + parameters_json（temperature, contextWindowLength, timeoutSeconds 等）+ status + last_test_*
  - 新增 CatalogProvider / CatalogModelItem 两个只读市场目录对象（不属于用户私有数据）
  - 新增 DefaultModelSettings（偏好的一部分）：`{ defaults: { LLM: profileKey?, EMBEDDING: profileKey?, ... } }`
  - 业务规则：
    - BR-015（级联）：模型名称必须从该 provider 的 catalog 中选择（自定义 CUSTOM 提供商除外，此时名称仍允许手填并给出提示）
    - BR-016（默认模型）：新建知识库的 model_binding 若未手动选，则按该知识库的用途类型从 DefaultModelSettings.defaults[usage_type] 取对应 profileKey（由知识库设置页继承）
    - BR-017（枚举兼容）：CHAT 作为历史值只在读取时做别名 → LLM；所有写入场景显式拒绝 `CHAT` 并提示错误「值已升级为 LLM」
- 状态流转影响：不适用；连接与档案的 ACTIVE/DISABLED 状态流转保持不变；测试状态 last_test_* 语义不变；知识库文档状态机不变（REQ-012 / BR-007 延续）
- 事务/一致性影响：
  - 连接 + 档案创建为两次独立 HTTP 调用，不要求分布式事务（失败时手动重试/删除）
  - 写入 preferenceJson.defaultModels：后端 `PATCH /users/me` 整体单写，局部 JSON 合并在应用层执行（先 GET → 合并 → 再 UPDATE）；需防止并发覆盖—— 延续现有 `PATCH /users/me` 语义即可，冲突概率低，若出现用户可再保存一次
- 工具包/依赖影响：不适用；不引入新第三方库；Ragflow 交互骨架纯 ant-design-vue 组件（a-layout/a-row/a-col/a-radio-group/a-select/a-card/a-tag/a-input-search）

## 7. 数据结构与 DDL 影响

- 是否涉及持久化结构变更：否（主结构两张表已存在，无新增表无合并表）；但有「枚举白名单扩展 + 存量值兼容」的轻量数据变更。
- SQL 当前结构快照：
  - 原始 DDL 位置：`fons4ai-rag2okf-service/sql/init-schema.sql`
  - 关键证据（L3）：
    - `kb_model_connection`（L26~L51）：connection_key, display_name, provider_code, provider_name, base_url, api_key_ciphertext/nonce/key_version/mask, status, last_test_*
    - `kb_model_profile`（L53~L74）：profile_key, connection_id, model_type VARCHAR(24), model_name, dimensions, parameters_json JSON, status, last_test_*
    - `kb_model_binding`（L76~L93）：usage_type VARCHAR(40), model_profile_id
    - `kb_user.preference_json` JSON（用户表 L13）
- SQL DDL 动作：无新增/重命名/ALTER；仅**执行型数据变更脚本**（建议性，用户或 DBA 手动执行）：
  - 存量 `CHAT → LLM` 转换 UPDATE（可选执行；若不执行应用层读取别名也可兼容，但为数据一致性推荐执行）
  - 若后续把 model_type/usage_type 从 VARCHAR 改为真正 ENUM 类型才需要 ALTER；当前建议维持 VARCHAR（灵活性好）。
- DDL 分组：同一库 rag2okf + 同一模型域
- 存量表原始 DDL：已存在（init-schema.sql）
- 执行型变更 DDL（建议性）：
  - 路径：`spec/features/20260807/ddl-changes/CR-001-rag2okf-kb_model_profile-chat-to-llm.sql`
  - 内容：两条 UPDATE + 注释说明可选执行；无 DDL；不会改变表结构
- DDL 执行方式：用户/DBA 手动执行（可选，不阻塞上线；不执行应用层兼容）
- DDL 执行确认：不适用（不改变结构，不阻塞）

## 8. 回归与回滚

- 回归风险：有，3 项
  1. T006/T011 已通过的合并表单测试被替换 → 新 composable + 视图必须通过同级覆盖（防止质量回退）
  2. 7 类型枚举扩展后，知识库 model_binding usage_type 白名单若有遗漏会导致绑定失败 → T032 专门梳理白名单
  3. preferenceJson.defaultModels 子节点合并时，后端若直接整字段覆盖会导致其他偏好丢失 → 应用层代码审查或集成测试确认合并
- 回归验证范围：
  - 单元测试：`models.spec.ts`、四个 composable（providerForm/profileForm/catalog/defaultModels）、`Rag2OkfAppShell` 的设置直达
  - 视图测试：`ModelSettingsView.spec.ts`（左右双栏 + 7 区用例）、`KnowledgeBaseSettingsView`（7 绑定槽 + 跟随默认）
  - 集成：T013 demo 模式重跑、T017 real 联调、T018 业务状态机回归、T019 视觉回归
  - e2e：`model-settings.spec.ts` 更新新交互
- 回滚方案：
  - **前端回滚**：git revert 本次 T025~T031 的前端改动 + 恢复 T006/T011 旧版本即可（旧代码在历史中）
  - **后端回滚**：新增 /model-catalog 接口纯只读，可直接下线无影响；枚举白名单扩展可直接 revert
  - **数据回滚**：若已执行了 UPDATE `CHAT → LLM`，可反向 UPDATE `LLM → CHAT`（建议执行前先备份）。由于应用层做双向兼容，来回切换不会影响业务读写。
- S2 风险门禁：需要
  - 门禁 1：T013 demo 集成必须通过，新交互全流程无明显缺陷，才可继续 T032 后端实现
  - 门禁 2：`preferenceJson` 局部合并的代码评审后，才可进入 real 模式联调
  - 门禁 3：枚举扩展白名单（7 类型 × 连接/档案/binding 三处）统一在一个常量文件中集中维护，避免散落硬编码导致不同模块识别不一致

## 9. 长期知识影响

- 是否产生长期知识影响：是
- 影响类型：数据结构 + 接口契约 + 业务规则
- 影响说明：
  - **数据结构**：Rag2OKF 模型域长期采用「模型提供商（连接+凭证）」与「模型档案（具体模型）」两层结构，不再合并为单一模型配置概念。1:N 关系（一个凭证挂多个模型）为标准心智。
  - **接口契约**：模型域标准 API 为两步式（/model-connections + /model-profiles），配套只读的 /model-catalog 市场目录；默认模型配置通过 PATCH /users/me 的 preferenceJson.defaultModels 节点存储（不单独建表，复用既有偏好基础设施）。
  - **业务规则**：模型名称在支持的提供商下必须从市场目录级联选择，保证模型名与能力标签一致；CUSTOM 自定义提供商仍可手填。模型类型统一 7 枚举（LLM/Embedding/Rerank/TTS/ASR/VLM/OCR），历史 CHAT 值应用层别名映射为 LLM。
- 处理边界：知识沉淀由 `fons4ai-knowledge-summary` 在用户显式触发后处理，本 CR 不生成知识同步任务或知识汇总交接任务。

## 10. 文档更新

- `前端交互体系重设计-需求说明书.md`：
  - 更新章节：需求澄清摘要 Q3 结论；需求列表 REQ-004 替换 + 新增 REQ-017~REQ-019；业务规则 BR-004 替换 + 新增 BR-015~BR-017；AC-008/AC-009 替换 + 新增 AC-026~AC-032；影响说明 + 版本修订记录（V1.4.0）
- `前端交互体系重设计-技术设计说明书.md`：
  - 更新章节：§3.1 模型设置 API（整节替换为两步式 + 模型目录 + 默认模型偏好）；§3.2 知识库 API 中 binding usage_type 扩展；§4.2 字段映射契约整节替换为两张表的 7 类型映射；新增 §4.8 默认模型偏好 JSON 结构详设；§5.7 模型设置页面交互详设整节重写为 Ragflow 双栏；版本修订记录（V1.4.0）
- `前端交互体系重设计-任务规划.md`：
  - 追加章节：CR-001 兼容与方向撤销说明（T014/T015/T020/T021 停止执行的处理）；追加增量任务 T025~T033 共 9 条；新增 AC 映射；风险与依赖关系更新；版本修订记录 V1.4.0
- 变更记录：已在三件套各自的版本修订记录追加

## 11. 增量任务

可执行增量任务必须追加到 `前端交互体系重设计-任务规划.md`。本节只记录新增任务摘要和任务 ID，便于 CR 追踪。

| 任务 ID | 任务标题 | AC | 追加位置 |
| --- | --- | --- | --- |
| T025 | 前端 API 层撤销合并契约，恢复两步式 + 新增目录/偏好 API | AC-026/027/028/031 | 任务规划.md |
| T026 | 新增 useModelCatalog composable + useDefaultModels composable | AC-026/028 | 任务规划.md |
| T027 | 拆分 useModelForm 为提供商表单（3 字段）+ 模型档案级联表单 | AC-027/030/009/031 | 任务规划.md |
| T028 | 重写 ModelSettingsTab 为 Ragflow 左右双栏骨架 | AC-026/028/029/032 | 任务规划.md |
| T029 | 实现右侧模型市场（搜索/类型标签/ProviderCard 网格点击驱动添加） | AC-026 | 任务规划.md |
| T030 | 实现左侧默认模型配置 7 下拉 + 按提供商分组的已添加模型卡 | AC-028/029/031 | 任务规划.md |
| T031 | 实现新增/编辑/测试/替换 Key Modal 两级（三字段提供商 → 级联档案） | AC-027/030/AC-008 新/AC-009 新 | 任务规划.md |
| T032 | 后端新增 /model-catalog 接口 + 7 类型白名单 + binding 兼容 + 存量 CHAT 迁移 SQL | AC-031 | 任务规划.md |
| T033 | 重写模型设置相关单元/集成/e2e 测试 + T013 demo 集成重跑 | AC-026~032 + AC-008~009 新 | 任务规划.md |

### 11.1 任务规划追加片段

以下片段已追加到 `前端交互体系重设计-任务规划.md` 的增量任务区（紧接 T024 之后），并在依赖图中连接。

- [ ] T025 前端 API 层撤销合并契约，恢复两步式 + 新增目录/偏好 API
  - 通俗解释: 前端 models.ts 删除合并的 ModelConfig/SaveModelConfigInput 及单步 CRUD 函数，恢复为 /model-connections 和 /model-profiles 两步式 API（与后端已实现的 Controller 对齐）；新增 GET /model-catalog 返回模型市场目录；新增通过 PATCH /users/me 读写 preferenceJson.defaultModels 的便捷函数。
  - AC: AC-026, AC-027, AC-028, AC-031
  - 来源: CR-001 §5
  - Files: fons4ai-rag2okf-ui/src/api/models.ts; fons4ai-rag2okf-ui/src/api/mock/models.ts; fons4ai-rag2okf-ui/src/api/auth.ts（或新增 users.ts 放 GET/PATCH /users/me 的便捷调用）; fons4ai-rag2okf-ui/src/api/__tests__/models.spec.ts
  - Depends: 无（独立于视图）
  - Verification: 给定 demo 模式，调用 listModelConnections 返回 connection 列表，调用 createModelConnection({displayName,baseUrl,apiKey}) 走三字段创建；调用 list/POST/PATCH /model-profiles 两步式可用；给定 listModelCatalog() 返回含 providers + typeCounts 的目录；给定 setDefaultModels({LLM:profileKey}) 后，读取 GET /users/me 的 preferenceJson 可见 defaultModels 节点。
  - Quality: DDD-lite：模型连接与模型档案各自独立类型和 API，互相不混字段；模型目录 CatalogProvider/CatalogModelItem 独立为纯读类型；preferenceJson.defaultModels 合并逻辑（读写时仅处理该子节点，保留其他偏好）封装在 api/users.ts，不把 JSON 结构细节暴露给视图；modelType 7 枚举常量集中定义（常量文件：types/model.ts），不散落硬编码；旧的 /model-configs 代码不残留于导出（可在内部保留兼容层 1 个版本，但不得 public export）。
  - Done: API 层两步式可用；模型目录和偏好读写便捷函数可用；单元测试覆盖两步式 + 目录；demo mock 数据对齐新结构；类型检查通过。

- [ ] T026 新增 useModelCatalog composable + useDefaultModels composable
  - 通俗解释: 新增两个 composable：useModelCatalog 承载右侧市场的搜索、类型标签筛选、provider 网格过滤和选中触发添加回调；useDefaultModels 承载左侧 7 下拉的默认模型配置（从 preferenceJson 解析 → 保存时写回 preferenceJson.defaultModels，局部合并不覆盖其他偏好）。
  - AC: AC-026, AC-028
  - 来源: CR-001 §5, §6
  - Files: fons4ai-rag2okf-ui/src/composables/useModelCatalog.ts; fons4ai-rag2okf-ui/src/composables/useDefaultModels.ts; fons4ai-rag2okf-ui/src/composables/__tests__/useModelCatalog.spec.ts; fons4ai-rag2okf-ui/src/composables/__tests__/useDefaultModels.spec.ts
  - Depends: T025
  - Verification: 给定 useModelCatalog.load()，当 keyword="deep" 且 activeType="LLM" 时，filteredProviders 只返回名称匹配且支持 LLM 的 providers；计数 countByType('EMBEDDING') 正确。给定 useDefaultModels，当 setDefault('LLM', profileKey) 并 saveAll() 后，后端 PATCH 请求体中的 preferenceJson.defaultModels.LLM === profileKey，且原有其他偏好节点如 language/theme 不变（T013 demo 模式下本地 mock 也保证不覆盖其他 key）。
  - Quality: DDD-lite：useModelCatalog 内部封装查询/过滤/搜索 debounce，视图只暴露 filteredProviders、typeCounts 和 actions（selectProvider/setKeyword/setType），不暴露原始 catalog 状态修改细节；useDefaultModels 负责 7 枚举默认值与 preferenceJson 节点之间的双向转换（含读取 fallback 兼容 CHAT→LLM），并在修改前后做浅合并；两个 composable 都支持 loading/saving 状态；不直接操作 localStorage（偏好由后端 preferenceJson 持久化）；demo 模式下写入内存 mock，不真实调用后端。
  - Done: composable 可用；单元测试覆盖搜索/筛选、偏好读写不覆盖其他节点、7 类型白名单、CHAT 别名兼容。

- [ ] T027 拆分 useModelForm 为提供商表单（3 字段）+ 模型档案级联表单
  - 通俗解释: 撤销旧的 useModelForm（单表单合并连接+档案），拆为两个 composable：useModelProviderForm 承载提供商连接三字段表单（displayName/baseUrl/apiKey，新建时 apiKey 必填 + 校验；编辑只替换 key 独立入口），useModelProfileForm 承载模型档案表单（provider 外键 + 类型 7 选 1 + 从 catalog 级联选择 modelName + 按类型显示不同高级参数）。
  - AC: AC-027, AC-030, AC-009（新版）, AC-031
  - 来源: CR-001 §5, §6
  - Files: fons4ai-rag2okf-ui/src/composables/useModelProviderForm.ts; fons4ai-rag2okf-ui/src/composables/useModelProfileForm.ts; 删除或重命名旧 useModelForm.ts（保留 git 历史）；fons4ai-rag2okf-ui/src/composables/__tests__/useModelProviderForm.spec.ts; fons4ai-rag2okf-ui/src/composables/__tests__/useModelProfileForm.spec.ts
  - Depends: T025, T026（级联需要 catalog）
  - Verification: 给定 useModelProviderForm 的 prepareCreate()，validate() 返回「displayName 必填/baseUrl 必填/apiKey 必填」3 条错误；准备编辑模式后 apiKey 不回显。给定 useModelProfileForm，当 providerCode=DEEPSEEK 且选中类型=LLM 时，availableModelNames 只列出 DeepSeek LLM 模型清单（来自 catalog）；非 CUSTOM 提供商 modelName 文本输入禁用；保存高级参数时，RERANK 类型下 temperature 不参与校验也不提交。
  - Quality: DDD-lite：校验规则集中在 composable 内；级联选择逻辑 availableModelNames computed 从 catalog + providerCode + selectedType 三输入纯函数推导，视图不手填模型名（CUSTOM 除外并在 Quality 中明确例外）；高级参数按 7 类型分别定义字段分组，视图渲染据此折叠，不在视图中写 if-else 控制参数集合；CUSTOM 提供商仍允许手填模型名，给出「该提供商不在市场目录，名称请确保与厂商官方一致」的轻提示。
  - Done: 两个 composable + 测试通过；provider 三字段表单必填校验覆盖；档案级联模型名选择与类型驱动的高级参数分组生效；CUSTOM 模式降级手填并有提示。

- [ ] T028 重写 ModelSettingsTab 为 Ragflow 左右双栏骨架
  - 通俗解释: 把现有的表格 + 详情卡结构整体重写为 a-layout 左右双栏布局骨架（约 2/3 + 1/3），左侧包含默认模型卡区占位 + 已添加模型分组卡区占位，右侧包含搜索区 + 类型标签区 + provider 网格卡区；顶部账号菜单的设置点击后默认定位该 Tab。
  - AC: AC-026, AC-028, AC-029, AC-032
  - 来源: CR-001 §5, 需求新 REQ-019
  - Files: fons4ai-rag2okf-ui/src/views/settings/ModelSettingsTab.vue（模板+脚本重写）; fons4ai-rag2okf-ui/src/layouts/Rag2OkfAppShell.vue（设置入口直达参数）；fons4ai-rag2okf-ui/src/views/settings/__tests__/ModelSettingsView.spec.ts（骨架渲染用例重写）
  - Depends: T026
  - Verification: Given 进入 /settings?tab=model-providers，当页面加载完成后，左侧有两块区域（上方设置默认模型占位、下方已添加模型分组占位），右侧有 3 块区域（搜索框、类型标签含 All/7 类、ProviderCard 网格）；Given 点击顶部账号菜单的设置入口，当路由跳转后，激活 Tab 定位在「模型提供商」并选中。
  - Quality: 三主题下左右双栏样式一致；窄屏（<992px）自动降级为上下堆叠（先左栏后右栏）保证移动端可滚动；所有新增占位区域在数据加载完毕后被真实内容替换，空状态使用 a-empty 组件显示合理文案；Rag2OkfAppShell 账号菜单设置入口使用 router.push({ path: '/settings', query: { tab: 'model-providers' } }) 直达；设置页读取 query.tab 映射当前 Tab。
  - Done: 左右双栏骨架渲染正确；窄屏降级；顶部直达入口路由正确；空状态合理；视图测试覆盖骨架与入口直达。

- [ ] T029 实现右侧模型市场（搜索/类型标签/ProviderCard 网格点击驱动添加）
  - 通俗解释: 右侧市场真实填充数据：搜索框 debounce 200ms 同时匹配 provider 名称和旗下 model；类型标签栏使用 a-radio-group 或自定义 a-tabs 带 8 个标签并显示每类数量；ProviderCard 网格显示 Logo/名称/官方链接↗/支持的类型 tags；点击整张 ProviderCard 触发「从该提供商新建连接」的流程（打开 Modal 预填 provider 代码 + 默认 Base URL）。
  - AC: AC-026
  - 来源: CR-001 §5, Ragflow 截图
  - Files: fons4ai-rag2okf-ui/src/views/settings/ModelSettingsTab.vue（右侧栏实现）; 可选新增独立组件 ProviderMarketCard.vue；fons4ai-rag2okf-ui/src/views/settings/__tests__/ModelSettingsView.spec.ts（市场测试用例）
  - Depends: T026, T028
  - Verification: 给定 catalog 已加载，当输入关键字 "qwen" 时 Provider 网格仅剩 Tongyi-Qianwen；当点击 EMBEDDING 标签时，只有支持 EMBEDDING 的 providers 显示，标签上的数字为 25（与 typeCounts 对齐）；当点击某张 ProviderCard 时，openCreateProvider 事件被触发，携带 catalog.providerCode 与 defaultBaseUrl，对应 Modal 打开并预填。
  - Quality: 搜索 debounce 避免连续刷新；类型标签点击状态即时切换；ProviderCard 整张可点击同时不干扰右上角官方↗新标签页跳转（a 标签使用 stop/self 修饰）；Logo 显示可先使用 ant-design 的图标按 providerCode 映射（如 AI/CloudServer 等），不引入远程图片依赖；类型标签颜色语义与左侧默认模型/档案卡保持一致（LLM=蓝、Embedding=绿、Rerank=橙、TTS=紫、ASR=青、VLM=红、OCR=灰）。
  - Done: 市场真实渲染 + 搜索 + 类型过滤 + 卡点击预填完整。

- [ ] T030 实现左侧默认模型配置 7 下拉 + 按提供商分组的已添加模型卡
  - 通俗解释: 左侧上方填充「设置默认模型」卡，7 个下拉按类型排列；下拉选项 = 当前用户已添加的同类型模型档案（displayName + providerName 后缀）；空类型显示「尚无该类型模型，请从右侧市场添加」提示。左侧下方按 providerCode 将已添加模型分组为 Card，每张卡头=Provider Name，每张卡体列出其下的档案行；每行包含 modelName、类型 tag、状态 tag、最近测试 tag；行末操作菜单含编辑/测试/删除档案；Provider 卡头含「展示更多模型▼添加」和「删除连接」按钮。
  - AC: AC-028, AC-029, AC-031
  - 来源: CR-001 §5
  - Files: fons4ai-rag2okf-ui/src/views/settings/ModelSettingsTab.vue（左侧实现）; fons4ai-rag2okf-ui/src/utils/groupBy.ts（或 reduce 内联）; fons4ai-rag2okf-ui/src/views/settings/__tests__/ModelSettingsView.spec.ts（左侧默认模型 + 分组卡测试）
  - Depends: T026, T027, T028
  - Verification: 给定当前用户有 1 条 LLM 档案，当查看默认模型卡的 LLM 下拉时，出现该档案为选项；当 providerCard 的展示更多▼添加被点击时，打开的档案 Modal 中该 provider 被锁定并预填；当档案行的测试被点击时，测试结果的 tag 直接在该行更新，不整页 loading，不影响其他行。
  - Quality: DDD-lite：providerCard 分组纯从 connections + profiles 做 LEFT JOIN 聚合（视图层），不改变领域对象；测试与删除的反馈归属化（行级独立 loading/error/success），符合 REQ-007；API Key 操作不在档案行中出现——替换 Key 入口在 Provider 卡头或连接操作菜单；替换流程同 AC-008 新语义。
  - Done: 默认模型 7 下拉 + 空状态；按 provider 分组卡片展示旗下档案行；行内操作反馈归属化。

- [ ] T031 实现新增/编辑/测试/替换 Key Modal 两级（三字段提供商 → 级联档案）
  - 通俗解释: 在骨架内接入两个 Modal：① 提供商连接 Modal：三字段表单（displayName/baseUrl/apiKey）新建或编辑；编辑模式下使用「替换 API Key」独立开关入口。② 模型档案 Modal：跟随选中的 provider，从 catalog 级联选择模型名称，支持编辑/新建；高级参数折叠按类型动态显示。测试 Modal：连接测试在 Provider 卡片底部按钮触发；档案测试在行内按钮触发。所有 Modal 统一为中央弹窗（沿用现有 a-modal + AntD 组件）。
  - AC: AC-027, AC-030, AC-008（新版两步）, AC-009（新版高级参数）
  - 来源: CR-001 §5
  - Files: fons4ai-rag2okf-ui/src/views/settings/ModelSettingsTab.vue（Modal 接入）; T027 两个 composable 复用（ProviderForm + ProfileForm）; fons4ai-rag2okf-ui/src/views/settings/__tests__/ModelSettingsView.spec.ts（新增/编辑/测试/替换 Key 用例）
  - Depends: T027, T028, T029, T030
  - Verification: 给定从右侧市场点击 ProviderCard，当打开连接 Modal 时，厂商代码和默认 Base URL 被预填；用户仅填写 displayName + apiKey 即可保存（3 字段）。保存成功后自动询问「是否立即添加模型」；点击是自动打开档案 Modal，provider 被锁定。档案 Modal 中选中类型=EMBEDDING 时，高级参数显示维度输入框，temperature 不出现；选中类型=LLM 时相反。
  - Quality: 未保存内容提示（离开 Modal 前检查 form dirty）；键盘可访问（Tab/Esc/Enter 同 T003 规范）；API Key 在取消/关闭/卸载时清理敏感字段（沿用 T011 的 clearSensitiveFields 策略）；两个 Modal 不冲突（打开另一个前关闭当前或状态互斥）；CUSTOM 提供商档案手填模型名时给出轻提示（T027 相同）。
  - Done: 两个 Modal + 行内测试/替换 Key 全流程可用；单元+视图测试覆盖。

- [ ] T032 后端新增 /model-catalog 接口 + 7 类型白名单 + binding 兼容 + 存量 CHAT 迁移 SQL
  - 通俗解释: 后端新增只读模型目录接口 GET /model-catalog；统一维护 7 枚举的集中常量文件，用于连接/档案/binding 三处白名单校验；知识库 usage_type 白名单新增 7 值与对应应用层映射；生成可选执行的 CHAT→LLM UPDATE SQL 草案；CHAT 别名映射在 Response 层生效。
  - AC: AC-031
  - 来源: CR-001 §6, §7
  - Files: fons4ai-rag2okf-service/src/main/java/com/fons/cloud/ai/rag2okf/common/constants/ModelType.java（新建，7 枚举集中常量 + CHAT 别名）; fons4ai-rag2okf-service/src/main/java/.../controller/ModelCatalogController.java（新建）; fons4ai-rag2okf-service/src/main/java/.../ModelCatalogApplicationService.java（新建，读取 catalog 资源）; fons4ai-rag2okf-service/src/main/resources/model-catalog.yaml（或 json；初始 provider→models 映射配置）; fons4ai-rag2okf-service/src/test/java/.../ModelCatalogControllerTest.java; spec/features/20260807/ddl-changes/CR-001-rag2okf-kb_model_profile-chat-to-llm.sql（新建，可选 UPDATE）
  - Depends: T013（demo 集成通过后再启动后端实现）；不依赖 T014（方向撤销）
  - Verification: Given 调用 GET /model-catalog?type=LLM，返回 providers 仅包含支持 LLM 的厂商，且 typeCounts.LLM == 数量；Given 读取 profile 时，model_type 为 'CHAT'，Response 转换为 'LLM' 并保留原始字段用于兼容；Given 写入 profile 时提交 model_type='CHAT'，校验返回 400 拒绝；Given 创建知识库 binding 的 usage_type='RERANK'，应用层白名单校验通过。
  - Quality: 7 枚举常量集中一处维护；model-catalog.yaml 可通过资源文件热更新，不硬编码；SSRF 校验不作用于目录默认值；catalog 接口可加轻量本地缓存 10min；CHAT 别名映射在 Response DTO 层统一处理，不侵入底层存储；迁移 SQL 标注可选执行，不执行时别名仍可生效；敏感信息不在 catalog 接口输出。
  - Done: 目录接口可用；7 枚举白名单一致；CHAT→LLM 别名映射生效；binding 白名单扩展；可选 UPDATE SQL 草案生成；后端测试覆盖枚举白名单与别名。

- [ ] T033 重写模型设置相关单元/集成/e2e 测试 + T013 demo 集成重跑
  - 通俗解释: 替换或新增对应 T025~T032 改动的所有单元/视图/e2e 测试；重跑 T013 demo 集成（含新模型设置交互流）；检查 lint/typecheck。
  - AC: AC-026~032 + 新版 AC-008/009
  - 来源: CR-001 §8
  - Files: fons4ai-rag2okf-ui/src/api/__tests__/models.spec.ts; fons4ai-rag2okf-ui/src/composables/__tests__/（4 个 composable spec，见 T026/T027）; fons4ai-rag2okf-ui/src/views/settings/__tests__/ModelSettingsView.spec.ts; fons4ai-rag2okf-ui/src/layouts/__tests__/Rag2OkfAppShell.spec.ts（设置入口）; fons4ai-rag2okf-ui/src/__tests__/demo-integration.spec.ts（T013 重跑更新）; fons4ai-rag2okf-ui/e2e/model-settings.spec.ts; fons4ai-rag2okf-service 测试（T032 新增）
  - Depends: T025~T032
  - Verification: 给定 vitest 执行，单元测试全通过；给定 demo 集成 T013 执行，当用户从顶部导航进入模型设置 → 在右侧市场点击 Tongyi-Qianwen → 三字段新建连接 → 在其下添加 LLM 和 EMBEDDING 两个模型 → 保存默认模型 → 返回知识库，每步交互无整页冻结、反馈归属化、三主题一致；typecheck 与 lint 通过，无类型错误。
  - Quality: 新测试覆盖新 AC 的正负用例；旧的合并单表单测试若不再适用则标记 skip 后删除（保留 git 历史）；demo mock 数据与 T025 API 契约一致；e2e 不引入真实 key 凭证。
  - Done: 测试全通过；demo 集成重跑通过；lint/typecheck 通过。

## 12. 实现确认门禁

- 状态：等待用户确认
- 规划产物不等于实现授权。
- 生成本 CR 和增量任务后必须暂停，等待用户确认后才能进入业务代码实现。
- 用户确认执行且未指定任务 ID 时，默认执行全部未完成任务（含 CR-001 新增的 T025~T033，以及原任务规划中尚未执行的 T013、T016~T019、T021。T014/T015/T020 已被 CR-001 方向撤销，不执行，保持 pending + 注释）。
- 用户指定任务 ID 时，例如 `执行 T025,T026`，只执行指定任务。
- 确认执行后默认执行全部未完成任务；如需指定范围，请回复：执行 T001,T002。
