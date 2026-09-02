# 文档解析样本回归清单

本目录只允许放置脱敏或合成的测试文件，不得提交业务正文、凭据、对象键、真实人员信息或可识别的企业资料。

本清单冻结 T032 发现的回归范围，不表示列出的格式已经被当前实现成功解析。格式能力、页级锚点和 OCR 行为由 T033～T037 分别验证。

| 场景 | 计划样本名 | 格式 | 最低检查点 | 当前状态 | 后续归属 |
| --- | --- | --- | --- | --- | --- |
| 纯文本 | `text-basic.txt` | TXT | 多段文本和空行保留为解析原料 | 待添加 | T035、T037 |
| Markdown | `markdown-structure.md` | Markdown | 标题、列表、代码和表格 | 待添加 | T035、T037 |
| 电子 PDF | `pdf-text-layer.pdf` | PDF | 文本层、页码和 OCR 计划 `NONE` | 待添加 | T034、T035、T037 |
| 扫描 PDF | `pdf-scanned.pdf` | PDF | 无有效文本层和 OCR 计划 `REQUIRED` | 待添加 | T034、T033、T037 |
| 混合 PDF | `pdf-mixed.pdf` | PDF | 文本页与扫描页，以及 `PAGE_SELECTIVE` | 待添加 | T034、T033、T037 |
| Word | `docx-structure.docx` | DOCX | 标题、段落、表格和图片引用 | 待添加 | T035、T037 |
| 图片 | `image-text.png` | PNG | OCR 区域、阅读顺序和置信度 | 待添加 | T033、T035、T037 |
| 音频 | `audio-speech.wav` | WAV | 转写文本和时间锚点 | 待添加 | T035、T037 |

当前仓库在 T032 前没有 `src/test/resources` 文档样本。T032 只建立命名、覆盖和责任基线；不得据此把任意样本标记为已验证。
