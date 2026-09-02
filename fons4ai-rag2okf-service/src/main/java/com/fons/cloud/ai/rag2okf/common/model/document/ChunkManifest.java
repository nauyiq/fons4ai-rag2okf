package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ParsedDocument 按冻结策略生成的不可变分块清单。
 *
 * <p>清单仅记录可追溯内容、策略和统计，不保存 MinIO 对象键、模型凭据或任务运行状态。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChunkManifest {

    /** 所属文档业务标识。 */
    private String documentKey;

    /** 所属解析结果业务标识。 */
    private String resultKey;

    /** 生成本清单时冻结的分块策略。 */
    private ChunkPolicy chunkPolicy;

    /** 按序输出的块清单。 */
    private List<ChunkManifestItem> chunks = new ArrayList<>();

    /** 父块数量。 */
    private int parentCount;

    /** 子块数量。 */
    private int childCount;

    /** 所有块总数。 */
    private int totalCount;

    /** 清单规范化字节的 SHA-256 摘要。 */
    private String contentHash;
}
