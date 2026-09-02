package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkRole;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ChunkManifest 中一条可追溯分块记录。
 *
 * @author hongqy
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChunkManifestItem {

    /** 当前清单内唯一且由输入内容确定的块标识。 */
    private String chunkId;

    /** 块在清单中的连续序号。 */
    private int ordinal;

    /** 块的层级角色。 */
    private ChunkRole role;

    /** 父块标识；平铺块和父块为空。 */
    private String parentChunkId;

    /** 是否允许后续发布流程将本块送入向量化。 */
    private boolean embeddingEligible;

    /** 本块覆盖的 ParsedBlock 标识，按原文顺序保存。 */
    private List<String> sourceBlockIds = new ArrayList<>();

    /** 本块最早内容的来源锚点，用于预览和回溯。 */
    private SourceAnchor sourceAnchor;

    /** 本块覆盖的全部来源锚点，按原文顺序保存。 */
    private List<SourceAnchor> anchorRefs = new ArrayList<>();

    /** 规范化后的可展示、可嵌入文本。 */
    private String content;

    /** 由内容与来源块标识计算的 SHA-256 摘要。 */
    private String contentHash;
}
