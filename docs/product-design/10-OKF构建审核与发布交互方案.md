# OKF 构建审核与发布交互方案

> 状态：候选方案，等待确认。本文设计 OKF 候选的人工裁决、批量操作、来源核验和发布门禁，不代表已经授权实现。

## 1. 页面目标

OKF Build 审核页属于某一个知识库，服务于知识管理员：

```text
知识库 / 某个知识库 / OKF / 构建记录 / 某次 Build
```

页面只解决一个核心任务：

> 把机器提取的候选安全地裁决为确认、合并或排除，并确保最终知识可以回到有效原文证据。

它不是通用数据标注平台，也不是直接编辑 Parsed Document 的页面。

## 2. 输入与输出

### 2.1 Build 输入

OKFBuild 创建时冻结：

- `knowledgeBaseId`；
- `documentVersionId + parseRevisionId` 列表；
- Concept Type Template 版本；
- 提取模型、Prompt 和参数版本；
- 合并/相似度策略版本；
- 发起人和创建时间。

构建期间即使源文档产生新 ParseRevision，本次 Build 也不静默切换输入。页面显示“有更新的解析版本”提示，管理员可以继续审核冻结版本，或放弃并新建 Build。

### 2.2 审核输出

- 确认的新 Concept 草稿；
- 合并到已有 Concept 的变更草稿；
- 明确排除的候选及原因；
- 已确认的 ConceptRelation；
- 已验证的 Source Binding；
- 发布前 OKF Diff；
- Review Audit Log；
- 最终 BundleRevision。

审核编辑只改变 OKF 草稿，不反写 ParsedBlock、SourceDocument 或原始文件。

## 3. 两维状态模型

此前把 `BLOCKED` 与审核结果放在同一个状态枚举中，容易产生歧义。推荐改成两个正交维度。

### 3.1 ReviewStatus

```text
PENDING
CONFIRMED
MERGED
EXCLUDED
```

| 状态 | 含义 |
|---|---|
| PENDING | 尚未做出人工裁决 |
| CONFIRMED | 作为新概念进入本次 Bundle 草稿 |
| MERGED | 合并到已有 Concept 或本 Build 已确认概念 |
| EXCLUDED | 明确不进入 Bundle，必须保存原因 |

### 3.2 ValidationStatus

```text
PASS
WARNING
BLOCKED
```

| 状态 | 示例 |
|---|---|
| PASS | Schema、来源和关系均有效 |
| WARNING | 存在低相似度重复建议或非阻塞性提醒 |
| BLOCKED | 来源失效、关系端点缺失、Schema 不合法 |

UI 可以继续显示“阻塞”标签，但底层不应让它覆盖人工审核结果。例如一个已确认候选仍可能因 SourceAnchor 后续校验失败而处于 `CONFIRMED + BLOCKED`。

此状态拆分属于本阶段新增候选决策，需要确认后再同步修改原状态模型。

## 4. 候选审核动作

### 4.1 确认为新概念

前置条件：

- 标题、类型和正文通过模板 Schema；
- 至少一个有效 Primary Source Binding；
- 没有必须裁决的高相似度重复项；
- 所有关联端点可解析，或关系已经移除；
- 用户具有知识库 OKF 审核权限。

结果：

- `reviewStatus = CONFIRMED`；
- 保存人工修改后的 Candidate Draft；
- 保存 reviewer、时间和变更前后 Diff；
- 暂不生成正式 ConceptRevision，直到发布冻结。

### 4.2 合并到已有概念

合并前必须展示：

- 候选与目标 Concept 的标题、类型和正文差异；
- 双方 Source Binding；
- 当前 BundleRevision；
- 属性冲突；
- 合并后关系变化。

合并策略：

```text
标题              人工选择
类型              必须相同或显式变更
描述/正文          人工选择覆盖、保留或组合
结构化属性          冲突逐字段裁决
标签              去重并集
Source Binding     保留并集
Relation           去重后合并，非法关系阻塞
```

禁止仅凭相似度自动合并。金融制度中“同名但不同版本”“同名但适用产品不同”必须保留区别。

### 4.3 排除

排除原因使用受控枚举并允许补充说明：

```text
NOT_A_CONCEPT          不是独立知识概念
DUPLICATE_NO_MERGE     重复且无需补充已有概念
INSUFFICIENT_EVIDENCE  证据不足
WRONG_TYPE_OR_SCOPE    类型或知识库范围不合适
PARSER_NOISE           解析噪声
OTHER                  其他，必须填写说明
```

排除不删除候选，允许在本 Build 发布前恢复为 `PENDING`。

### 4.4 稍后处理

不改变状态，只保存当前编辑草稿并切换到下一候选。它不能被视为已审核。

## 5. 来源核验

### 5.1 Source Binding 最小结构

```yaml
candidateId: C-018
documentVersionId: fin-004-v2
parseRevisionId: parse-fin-004-v2-r1
sourceAnchorId: FIN-004@V2#eligibility.age
evidenceRole: PRIMARY
anchorStatus: VALID
excerptSnapshot: 借款人申请时，年龄应为23至58周岁。
```

### 5.2 有效来源条件

- 属于本次 Build 冻结的 ParseRevision；
- SourceAnchor 可以映射到 ParsedBlock；
- 引用片段与当前 Candidate Claim 一致；
- 文档未被永久删除；
- 当前审核人有权查看该来源；
- Primary Evidence 足以支持核心属性。

页面同时显示原文片段、文档版本、章节路径、页码和锚点精度。管理员可以打开原文，但不能在审核页直接修改原文。

### 5.3 多来源

候选可以绑定多个来源：

- `PRIMARY`：直接支持概念核心陈述；
- `SUPPORTING`：解释背景、生效范围或版本变化；
- `MENTION`：仅提及该概念，不足以支撑核心陈述。

发布门禁要求至少一个有效 `PRIMARY`，仅有 `MENTION` 不能确认。

## 6. 重复与合并建议

候选分别与以下范围比较：

1. 当前活动 Bundle 中的 Concept；
2. 本次 Build 已确认的新候选；
3. 同一 Build 仍待审核的候选。

相似度只是排序信号，不直接代表重复。建议综合：

- 标题规范化；
- 类型一致性；
- 结构化属性；
- Embedding 相似度；
- 来源文档与适用产品；
- 生效区间和版本。

候选阈值仅作为初始验证值：

```text
score >= 0.90    必须人工裁决后才能发布
0.75 <= score < 0.90    警告，可选择忽略并记录原因
score < 0.75     默认不显示，仍可在“全部建议”查看
```

阈值必须通过 OKF 黄金标注校准。

## 7. 关系审核

关系可以由模型建议，也可以由管理员创建或删除。

发布前校验：

- 起点和终点都能解析为本次草稿或活动 Bundle 中的 Concept；
- 类型模板允许该 Relation Type；
- 不允许自环，除非模板明确允许；
- 不重复创建完全相同的边；
- 关系自身有来源时保存 Source Binding；
- 合并候选后自动重新计算关系端点，但不自动解决语义冲突。

关系状态：

```text
SUGGESTED
CONFIRMED
REJECTED
BLOCKED
```

## 8. 安全批量操作

### 8.1 允许的批量动作

- 确认明确勾选且满足全部前置条件的候选；
- 按同一原因排除明确勾选的候选；
- 统一添加标签；
- 指派审核人（未来多人审核时）。

### 8.2 禁止的批量动作

- 不提供无条件“一键确认全部”；
- 不允许跨分页隐藏全选后直接确认；
- 不允许批量自动合并；
- 不允许跳过来源校验；
- 不允许把不同类型批量改成同一类型而不预览影响。

### 8.3 批量确认流程

```text
明确勾选候选
→ 系统过滤出可操作项
→ 展示可确认、跳过和阻塞数量
→ 预览变更
→ 用户再次确认
→ 逐项保存审核决定
→ 返回成功/失败明细
```

批量操作使用一个 `batchOperationId`，但每个 Candidate 保存独立审计记录，支持部分失败和幂等重试。

## 9. 发布就绪轨道

页面顶部使用一条与真实门禁对应的“发布就绪轨道”：

```text
来源有效
→ 重复建议已裁决
→ 关系端点完整
→ 人工审核完成
→ 可发布
```

它不是普通流程步骤，而是当前 Build 的实时质量摘要。点击某个节点会过滤左侧队列，例如点击“关系端点 57/58”只显示导致端点缺失的候选。

### 9.1 发布硬门禁

发布前必须同时满足：

- 所有候选均为 `CONFIRMED`、`MERGED` 或 `EXCLUDED`，不存在 `PENDING`；
- 进入 Bundle 的每个 Concept 至少一个有效 Primary Source Binding；
- 不存在 `ValidationStatus = BLOCKED`；
- 高相似度重复建议全部完成人工裁决；
- 所有确认关系的端点和类型合法；
- OKF Schema 和模板校验通过；
- 预生成 Bundle Diff 成功；
- 当前 Build 输入仍可访问。

任何硬门禁失败时，“发布 Bundle”按钮置灰，并展示可点击的具体阻塞数量。

### 9.2 发布预览

发布前展示：

```text
新增概念数量
修改已有概念数量
新增/删除关系数量
排除候选数量
来源绑定变化
index.md / Concept Markdown Diff
与当前 BundleRevision 的版本差异
```

管理员确认后才进入 OKF 发布 Saga。

## 10. 发布流程

```text
冻结 Review Decision
→ 生成 ConceptRevision
→ 生成/更新 ConceptRelation
→ 渲染 index.md、log.md 和 Concept Markdown
→ OKF v0.2 兼容子集校验
→ 保存 MinIO Bundle 快照
→ Concept Embedding
→ 写入 ES Concept 暂存索引
→ 数量、来源和字段校验
→ 激活 BundleRevision
```

发布要求：

- 幂等、可重试；
- 新 Bundle 发布失败时旧 Bundle 继续服务；
- 发布失败不丢失审核结果；
- ES 是可重建投影，不能反向成为 OKF 事实来源；
- 发布后的 Concept 修改必须通过新的 ConceptRevision/Build，不直接覆盖历史版本。

## 11. 并发与审计

### 11.1 并发

- Candidate 使用 `reviewVersion` 乐观锁；
- 打开详情时记录读取版本；
- 其他管理员已修改时禁止静默覆盖，展示差异并要求刷新；
- 批量操作逐项检查版本；
- 发布前再次执行全量 Readiness Check。

### 11.2 审计

每个动作记录：

- Build、Candidate、KnowledgeBase；
- 操作者与时间；
- 动作类型；
- 修改前后 Diff；
- 排除或忽略重复的原因；
- Source Binding 变化；
- `batchOperationId`；
- 客户端提交的 `reviewVersion`。

## 12. 异常与空状态

| 场景 | 页面行为 |
|---|---|
| 没有候选 | 说明可能没有可提取内容，提供查看提取日志和重新构建 |
| 新 ParseRevision 已出现 | 显示非阻塞提示，可继续冻结版本或新建 Build |
| SourceAnchor 失效 | Candidate 标记 BLOCKED，定位具体来源 |
| 目标 Concept 已被其他 Build 修改 | 阻止合并，展示最新 ConceptRevision |
| 并发冲突 | 不覆盖，要求查看差异后重试 |
| 发布失败 | 保留 Review Decision，旧 Bundle 继续服务，提供重试 |
| 原文件永久删除 | 来源不可展示且阻止相关概念发布 |

## 13. 页面结构

```text
┌──────────────────────────────────────────────────────────────┐
│ Build B-2026-003  等待审核           暂存  发布Bundle(禁用) │
│ 来源有效 ─ 重复建议 ─ 关系端点 ─ 人工审核 ─ 可发布          │
├──────────────┬─────────────────────────┬─────────────────────┤
│ 候选队列     │ 候选详情                │ 来源 / 关系 / 历史  │
│ 搜索/筛选    │ 标题、类型、描述、属性  │ 原文片段与锚点      │
│ 状态/类型    │ 合并建议与字段差异      │ 关系端点预览        │
│ 候选列表     │ 排除/稍后/确认           │ 查看原文            │
├──────────────┴─────────────────────────┴─────────────────────┤
│ 已选择3项，仅操作来源有效且无阻塞项      预览批量变更       │
└──────────────────────────────────────────────────────────────┘
```

## 14. V4 视觉与交互要求

- 延续 Spatial AI Knowledge Workspace 的窄侧栏、知识库级导航和三主题 Token；
- 页面主角是“候选—草稿—原文证据”的并排关系；
- 发布就绪轨道是唯一强调性视觉元素；
- 不用通用 KPI 卡片墙；
- 青绿只表示通过或确认，暖橙只表示阻塞、排除和风险；
- 相似度不能用绿色表达“正确”，使用中性的靛紫色；
- 原文高亮必须满足亮暗主题文本对比度；
- 所有仅图标按钮提供 Tooltip、可见焦点和辅助文本；
- 键盘可在候选列表、字段、来源和操作栏之间顺序导航；
- 发布、合并和批量排除需要二次确认，但普通字段暂存不弹窗。

## 15. 原型文件

```text
prototypes/v4/okf-build-review-light.png
prototypes/v4/okf-build-review-dark.png
```

亮暗主题保持完全相同的信息架构和业务状态，只切换语义 Token。

## 16. 本阶段确认门

进入下一阶段前需要确认：

1. 是否把 Candidate 状态拆成 ReviewStatus 与 ValidationStatus；
2. 是否要求所有 Candidate 都经过确认、合并或排除后才能发布；
3. 是否接受高相似度重复项必须人工裁决；
4. 是否接受批量确认仅针对明确选择且无阻塞的候选；
5. 是否接受合并逐字段裁决，不自动覆盖已有 Concept；
6. 是否采用 V4 OKF Build 审核页亮暗主题原型。
