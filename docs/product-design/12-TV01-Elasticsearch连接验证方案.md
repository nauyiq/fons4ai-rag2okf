# TV-01 Elasticsearch 连接验证方案

> 状态：待确认、待授权执行。
> 边界：本文只收敛技术实验，不安装 Elasticsearch、不创建产品模块、不编写知识库业务代码。

## 1. 当前结论

知识库 P0 推荐采用以下首轮基线：

```text
Java                         21
Spring Boot                  3.5.8
Spring Data Elasticsearch    5.5.6
Elasticsearch Java Client    8.18.8
Elasticsearch Server         8.18.x，优先精确对齐 8.18.8
Transport                    Spring Boot 原生自动配置
Hybrid Fusion                Java 应用层 RRF
```

不在 P0 再次升级到 Elasticsearch 9.x，理由是：

1. Fons4Cloud 刚完成旧客户端清理并验证 8.18.8 官方客户端；
2. 跨到 9.x 会同时引入客户端 major、Transport 和服务端运维变化；
3. 8.18 已覆盖 P0 所需 BM25、`dense_vector`、HNSW kNN、精确向量检索、过滤、Bulk 和 Alias 能力；
4. P0 的 RRF 在应用层实现，不依赖更高版本或特定商业授权能力。

该版本只能在 TV-01 连接和检索门禁通过后正式锁定。

## 2. 已验证事实与待验证边界

### 2.1 已在 Fons4Cloud 验证

- RHLC、Easy-ES 和重复 Elasticsearch 自动配置已删除；
- 依赖树只通过 Boot starter 引入 Spring Data Elasticsearch 5.5.6 和官方客户端 8.18.8；
- Boot 最小上下文可唯一装配 `ElasticsearchClient` 和 `ElasticsearchOperations`；
- 不可达地址会暴露可定位的连接异常；
- 测试捕获日志不包含用户名、密码和 API Key；
- Java 21 下模块测试与必要 Reactor 构建通过。

### 2.2 必须由知识库 TV-01 验证

- 服务端制品、客户端与服务端实际握手兼容性；
- TLS、Basic Auth/API Key 和最小权限账户；
- 索引创建、Mapping、Bulk、Refresh、查询和删除；
- 中文 Analyzer、BM25 与金融词汇效果；
- `dense_vector` Mapping、HNSW kNN、过滤和 exact 校准；
- 版本化物理索引、读写 Alias、切换与回退；
- `workspaceId`、`knowledgeBaseId`、`publicationRevisionId` 和发布状态过滤；
- 超时、部分 Bulk 失败、Alias 切换失败和服务端不可用语义。

## 3. 官方能力判断

Elastic 官方 8.18 文档确认：

- `dense_vector` 可以建立向量索引；
- 近似 kNN 使用 HNSW，支持 `k`、`num_candidates` 和过滤；
- `script_score` 可以作为小规模精确检索和 Recall 校准真值；
- 同一 Embedding 模型必须用于文档向量和查询向量；
- Java Client 在同 major 内提供向前 minor 兼容，但旧客户端不会自动拥有新服务端 API。

因此 P0 以同 minor 对齐为默认策略。执行前要从官方制品确认可用服务端 patch，并保证服务端 patch 不低于客户端兼容要求；若无法精确对齐 8.18.8，必须先形成单独兼容性结论，不能静默使用更旧服务端。

## 4. 实验环境

TV-01 使用隔离的本地或测试环境，不连接生产 Elasticsearch。

```yaml
experimentId: TV-01-ES-818
status: PLANNED
serverVersion: pending
javaClientVersion: 8.18.8
java: 21
springBoot: 3.5.8
license: pending
plugins: []
tls: required-test
authModes:
  - basic
  - api-key
dataset:
  functionalDocs: 100
  capacityChunks:
    - 10000
    - 100000
vectorDimensions:
  - 512
  - 1024
```

向量值在 TV-03 模型尚未确定前使用固定种子的合成向量。合成向量只能验证 API、过滤、性能趋势和 Recall 计算链路，不能证明中文语义检索质量。

## 5. 实验阶段

| 阶段 | 验证内容 | 主要证据 |
|---|---|---|
| TV-01A | 版本握手、集群信息、认证、TLS、最小权限 | Environment Manifest、请求摘要、错误摘要 |
| TV-01B | 创建版本化索引、Mapping、Bulk、查询、删除 | Mapping、Bulk 结果、索引统计 |
| TV-01C | 中文 Analyzer 与 BM25 基线 | Analyze Token、20 个 Smoke 的 Recall/MRR/nDCG |
| TV-01D | `dense_vector`、HNSW kNN、过滤、exact 校准 | kNN/exact 结果、Recall@20、P50/P95 |
| TV-01E | BM25 与 kNN 并行召回、应用层 RRF | 固定输入输出、RRF 可复现性、Query Trace 草案 |
| TV-01F | 读写 Alias 切换、指定旧版回退 | 切换前后可见版本、对账结果 |
| TV-01G | 超时、服务不可达、Bulk 部分失败、凭证脱敏 | 失败注入记录、重试结果、日志扫描 |

## 6. 最小索引投影

TV-01 只验证检索投影，不创建知识库业务表。

### 6.1 Chunk 索引

```text
id
workspaceId
knowledgeBaseId
sourceDocumentId
documentVersionId
publicationRevisionId
parentId
chunkType
headingPath
rawText
embeddingText
embeddingVector
sourceAnchorSummary
publicationStatus
```

### 6.2 必须验证的过滤条件

```text
workspaceId = 当前工作空间
knowledgeBaseId = 当前知识库
publicationRevisionId = 当前活动发布版本
publicationStatus = PUBLISHED
```

四项过滤必须同时适用于 BM25 与 kNN 路径。任何越权、错误版本或未发布 Chunk 被召回，TV-01 直接失败。

## 7. Alias 与回退场景

```text
kb-chunk-v1  <- kb-chunk-read / kb-chunk-write
kb-chunk-v2  <- 构建、校验，暂不可见
校验通过     -> 原子切换 kb-chunk-read 到 v2
回退         -> 明确切回 v1
```

需要证明：

- 切换前 v2 不会被正式查询读取；
- 切换后只有一个活动读版本；
- 回退后查询重新命中 v1；
- MySQL 活动版本与 ES Alias 的对账方案明确；
- 失败实验不删除旧索引，清理由单独动作执行。

## 8. 硬门禁

以下任一失败，不能锁定 Elasticsearch 版本：

- 客户端与服务端版本握手不满足官方兼容策略；
- Java Client 缺少 P0 必需 API；
- 权限、知识库、发布版本或状态过滤正确率低于 100%；
- BM25 与 kNN 使用不同过滤边界；
- Alias 切换出现两个活动版本或短暂读取未验证版本；
- exact 校准、Recall 指标或性能数据不可复现；
- 真实凭证进入代码、配置样例、日志或 Evidence；
- 不可达和部分失败被静默当作成功。

## 9. 通过条件

- 8.18.x 服务端与 8.18.8 Java Client 完成真实握手和全部 API 验证；
- BM25、kNN、应用层 RRF 和过滤链路均能运行；
- 10k/100k Chunk 形成容量与延迟基线；
- Alias 切换和指定版本回退可复现；
- 日志与 Evidence 不含真实凭证；
- 形成 `ADR-TV01-001`，明确服务端 patch、Analyzer、向量参数候选和已知限制。

通过 TV-01 只证明 Elasticsearch 基础能力可用，不代表 Embedding、Chunk 或 Reranker 已完成选型。

## 10. Evidence 目录

获得技术实验授权后再创建：

```text
docs/technical-spikes/TV-01-elasticsearch/
├── charter.md
├── environment.yaml
├── mappings/
├── requests/
├── runs/
├── evidence/
└── decision.md
```

不得保存真实密码、完整 API Key 或包含凭证的连接串。

## 11. 执行前确认项

执行 TV-01 前仍需用户确认：

1. 接受 Elasticsearch 8.18.x 同 minor 服务端为 P0 主候选，优先精确对齐 8.18.8；
2. 接受 9.x 延后到 P1 或框架再次升级时评估；
3. 接受应用层 RRF 作为 P0 固定基线；
4. 允许创建隔离 Elasticsearch 实验环境与 Evidence，但仍不编写知识库产品功能；
5. 提供或授权选择实验部署方式：Docker/Podman、本机压缩包或现有测试集群。

## 12. 官方参考

- [Elasticsearch Java Client 兼容策略](https://www.elastic.co/docs/reference/elasticsearch/clients/java)
- [Elasticsearch 8.18 kNN Search](https://www.elastic.co/guide/en/elasticsearch/reference/8.18/knn-search.html)
- [Elasticsearch Vector Queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/vector-queries.html)
- [Elasticsearch 8.18 迁移说明](https://www.elastic.co/guide/en/elasticsearch/reference/current/migrating-8.18.html)
