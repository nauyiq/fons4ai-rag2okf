# OKF 知识层方案

## 1. 定位

OKF 使用 Open Knowledge Format v0.2 的核心结构：Markdown、YAML Frontmatter、标准 Markdown 链接、`index.md` 和 `log.md`。

官方规范：https://github.com/GoogleCloudPlatform/knowledge-catalog/blob/main/okf/SPEC.md

平台中的职责关系：

| 对象 | 职责 |
|---|---|
| 原始文档 | 最终事实和引用依据 |
| Parsed Block | 文档结构化结果 |
| OKF Concept | 面向人和 Agent 的独立知识单元 |
| OKF Bundle | 某个知识库的可移植知识快照 |
| Chunk | 面向检索的派生切片 |

OKF 不替代 RAG，也不要求引入图数据库。

## 2. 产品归属

```text
KnowledgeBase 1 → 0..1 KnowledgeBundle
KnowledgeBundle 1 → * OKFBuild
KnowledgeBundle 1 → * KnowledgeConcept
KnowledgeBundle 1 → * BundleRevision
```

约束：

- 一个知识库最多一个逻辑 Bundle；
- 尚未构建 OKF 时 Bundle 可以不存在；
- Concept、Relation、Build 和 Revision 不能脱离知识库；
- 默认不允许跨知识库 ConceptRelation；
- 跨知识库问答可以联合检索多个 Bundle，但不能静默合并 Bundle。

## 3. 生成过程

UI 表达为“从知识库源文件构建 OKF”，后台统一从 Parsed Document 构建：

```text
多个 ParseRevision
→ 逐文档概念提取
→ Concept Candidate
→ 跨文档合并与去重
→ 关系建议
→ 人工确认
→ Java 渲染 Markdown/YAML
→ OKF 校验
→ BundleRevision
```

LLM 只输出受约束的结构化候选。Java 服务负责：

- 校验 Source Block；
- 生成稳定 Concept ID；
- 判断已有 Concept；
- 生成 YAML 和 Markdown；
- 维护版本与来源；
- 执行确定性规范校验。

不能把整个大知识库一次性塞给 LLM。使用逐文档 Map 和知识库级 Consolidation 两阶段处理。

## 4. 金融概念类型模板

第一阶段受控类型：

| 类型 | 含义 | 示例关系 |
|---|---|---|
| Product | 贷款产品 | CONTAINS Rule、REQUIRES Material |
| Policy | 制度和政策 | GOVERNS Product、DEFINES Rule |
| Eligibility Rule | 准入判断规则 | APPLIES_TO Product、BASED_ON Policy |
| Procedure | 业务流程 | APPLIES_TO Product、USES Material |
| Material | 申请或审批材料 | REQUIRED_BY Product |
| Definition | 术语定义 | EXPLAINS Rule |
| FAQ（可选） | 高频问答 | REFERENCES 其他 Concept |

类型模板定义：

- 推荐字段；
- Markdown 正文结构；
- 允许关系；
- LLM 抽取规则；
- UI 展示方式；
- 检索权重。

模板是轻量领域本体，不是 OKF 官方固定类型。

## 5. 候选确认

管理员可以：

- 确认新概念；
- 修改标题、类型、描述、标签和正文；
- 修改关系和来源绑定；
- 合并到已有概念；
- 排除不适合作为概念的内容。

Parsed Block 保持只读。修改已发布 Concept 时创建新 ConceptRevision。

可能重复的概念只提供建议，不自动合并。

候选审核、来源核验、批量操作和发布门禁的详细交互见 [10-OKF构建审核与发布交互方案.md](10-OKF构建审核与发布交互方案.md)。其中建议将人工审核结果与系统校验状态拆成两个正交维度，待确认后再更新本文件中的状态模型。

## 6. OKF 能否直接提供给 LLM

可以，但取决于规模：

- 小 Bundle：直接提供 `index.md` 和相关 Concept；
- Agent 使用：先读取目录，再沿链接逐步加载；
- 大知识库：先检索相关 Concept，再加载 Markdown 和原文证据。

OKF 是可供 LLM 阅读的知识组织格式，不等于把整个 Bundle 放入一个 Prompt。

## 7. 是否向量化

OKF 规范本身不要求向量化。

本平台推荐分别建立：

### Concept Vector

用于回答“问题涉及哪些概念”：

```text
type + title + description + tags + markdown body
```

### Chunk Vector

用于回答“原文依据具体写了什么”：

```text
heading path + source paragraph/table + context
```

不向量化：候选 Concept、内部 ID、时间戳、BundleRevisionId 等噪声字段。

## 8. OKF 与问答

```text
用户问题
→ Concept 召回
→ 一跳关系扩展
→ Source Binding
→ 原文 Chunk 召回
→ RRF/重排
→ 基于原文回答并引用
```

Concept 帮助理解和导航，最终金融事实仍然引用原始文档。

## 9. 发布

```text
冻结审核结果
→ ConceptRevision
→ index.md / log.md
→ OKF 校验
→ MinIO Bundle 快照
→ Concept Embedding
→ ES Concept 索引
→ 校验
→ 激活 BundleRevision
```

候选概念不向量化、不写 ES。新 Bundle 发布失败时继续服务旧 Bundle。

## 10. MVP 兼容边界

- 生成兼容 OKF v0.2 的核心字段和文件结构；
- 未识别扩展字段需要保留；
- 可读取并展示 `verified`、`stale_after`、`status` 等可选字段；
- 当前阶段不基于这些字段建立信任、新鲜度或冲突工作流；
- 暂不实现 Attested Computation 运行时；
- 支持导出，导入延期。

## 11. UI

OKF 页面位于：

```text
/knowledge-bases/{knowledgeBaseId}/okf
```

内部包含：

- 目录视图；
- 列表视图；
- 图谱视图；
- 概念详情和来源；
- Build 人工确认；
- Bundle 版本和导出。

视觉采用 V4 双主题 Spatial AI Knowledge Workspace。
