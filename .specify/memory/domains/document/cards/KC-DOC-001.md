# KC-DOC-001 文档稳定身份与当前源文件

> 知识编号：KC-DOC-001  
> 知识类型：数据模型  
> 所属领域：文档域（document）  
> 状态：已验证  
> 来源：user/docs/code  
> 可信度说明：目标模型由用户确认；CR-014/当前代码仅用于证明旧版本对象已移除。  
> 关联能力：当前文件管理  
> 关联适配：无  
> 关联场景：BS-DOC-001  
> 关联对象：SourceDocument  
> 关联代码/接口/SQL：`KbSourceDocumentEntity.java`、CR-014  
> 更新日期：2026-08-14

## 1. 事实描述

- 核心事实：SourceDocument 是稳定文档身份并保存当前源文件元数据；目标模型不建立 DocumentVersion 源文件版本链。
- 事实粒度：单一数据模型
- 适用范围：上传、更新、下载及后续处理归属。
- 不适用范围：ParseRevision、ChunkRevision、PublicationRevision 的历史修订。
- 证据依据：2026-08-14 用户确认。

## 2. 规则、流程或数据变化

| 项目 | 适用能力 | 适用适配 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| 源文件语义 | 当前文件管理 | 全部 | 更新文件替换当前引用，不生成业务版本链 | 已验证 |

## 3. 技术落地

- 入口：文档上传/更新/下载。
- 应用服务：DocumentApplicationService（参考）。
- 领域对象/方法：SourceDocument。
- 仓储/Mapper：SourceDocumentRepository/KbSourceDocumentMapper（参考）。
- 外部协作：ArtifactStore、知识库归属/授权边界。
- 测试：更新后当前文件、无 DocumentVersion、旧发布保留。

## 4. 关联知识

- 业务文档：`../文档域业务文档.md`
- 技术文档：`../文档域技术文档.md`
- 数据文档：`../文档域数据文档.md`
- 相关卡片：KC-DOC-003、KC-DOC-007

