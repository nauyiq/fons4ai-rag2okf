# KC-DOC-005 ParsedDocument JSON是规范解析制品

> 知识编号：KC-DOC-005  
> 知识类型：接口契约  
> 所属领域：文档域（document）  
> 状态：已验证  
> 来源：user/docs/code  
> 可信度说明：制品形式与不可变语义已确认，具体 Schema 待设计。  
> 关联能力：文档解析  
> 关联适配：全部解析器  
> 关联场景：BS-DOC-003/004  
> 关联对象：ParsedDocument、DocumentArtifactStore  
> 关联代码/接口/SQL：`ParseManifest.java`、`DocumentArtifactStore.java`（参考）  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：所有解析器必须产出经系统规范化和校验的不可变 ParsedDocument JSON；Markdown 只是可选派生物。
- 事实粒度：单一接口契约
- 适用范围：解析、预览、分块、导出。
- 不适用范围：把解析器原始响应或 Markdown 当作唯一事实源。
- 证据依据：2026-08-14 用户确认。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 确定性 | 文档解析 | 全部 | 固定Schema且生成后不可变；LLM重跑不承诺字节一致 | 已验证 |

## 3. 技术落地

- 入口：DocumentParser 输出。
- 应用服务：解析任务编排。
- 领域对象/方法：Normalizer、Validator、ParsedDocument。
- 仓储/Mapper：ParseRevision artifactRef。
- 外部协作：MinIO ArtifactStore。
- 测试：跨解析器契约、Schema错误、锚点和不可变性。

## 4. 关联知识

- 业务文档：`../文档域业务文档.md`
- 技术文档：`../文档域技术文档.md`
- 数据文档：`../文档域数据文档.md`
- 相关卡片：KC-DOC-003、KC-DOC-004、KC-DOC-006

