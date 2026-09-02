package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy;

import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 边界策略生成、尚未持久化为 Manifest 的候选内容块。
 *
 * @author hongqy
 */
public class ChunkCandidate {

    /** 候选块文本。 */
    private final String content;

    /** 覆盖的原始块标识。 */
    private final List<String> sourceBlockIds;

    /** 候选块覆盖的全部来源锚点，按原文顺序保存。 */
    private final List<SourceAnchor> anchorRefs;

    /** 已由 SDK/Fons 扩展确定的层级角色；未确定时为 FLAT。 */
    private final ChunkRole role;

    /** 运行期临时关联键，不能进入持久化 ChunkManifest。 */
    private final String candidateKey;

    /** 运行期父关联键，仅 CHILD 使用。 */
    private final String parentCandidateKey;

    /**
     * 创建没有显式父子关系的平铺候选块。
     *
     * @param content 候选文本
     * @param sourceBlockIds 已证明覆盖的来源块标识
     * @param sourceAnchor 来源锚点
     */
    public ChunkCandidate(String content, List<String> sourceBlockIds, SourceAnchor sourceAnchor) {
        this(content, sourceBlockIds, List.of(sourceAnchor), ChunkRole.FLAT, null, null);
    }

    /**
     * 创建携带完整来源和运行期层级关系的候选块。
     *
     * @param content 块文本
     * @param sourceBlockIds 已证明覆盖的来源块标识
     * @param anchorRefs 已证明覆盖的来源锚点
     * @param role 块角色
     * @param candidateKey 运行期关联键
     * @param parentCandidateKey 运行期父关联键
     */
    public ChunkCandidate(String content, List<String> sourceBlockIds, List<SourceAnchor> anchorRefs,
                          ChunkRole role, String candidateKey, String parentCandidateKey) {
        this.content = content;
        this.sourceBlockIds = List.copyOf(new ArrayList<>(sourceBlockIds));
        this.anchorRefs = List.copyOf(new ArrayList<>(anchorRefs));
        this.role = role == null ? ChunkRole.FLAT : role;
        this.candidateKey = candidateKey;
        this.parentCandidateKey = parentCandidateKey;
    }

    /** @return 候选块文本。 */
    public String content() {
        return content;
    }

    /** @return 覆盖的来源块标识。 */
    public List<String> sourceBlockIds() {
        return sourceBlockIds;
    }

    /** @return 首个来源锚点；没有来源时返回 null。 */
    public SourceAnchor sourceAnchor() {
        return anchorRefs.isEmpty() ? null : anchorRefs.getFirst();
    }

    /**
     * 返回全部来源锚点。
     *
     * @return 已证明覆盖的锚点列表
     */
    public List<SourceAnchor> anchorRefs() {
        return anchorRefs;
    }

    /** @return SDK/Fons4AI 已确定的运行期层级角色。 */
    public ChunkRole role() {
        return role;
    }

    /** @return 运行期候选块关联键，不进入持久化清单。 */
    public String candidateKey() {
        return candidateKey;
    }

    /** @return 运行期父候选关联键，仅 CHILD 使用。 */
    public String parentCandidateKey() {
        return parentCandidateKey;
    }
}
