package com.fons.cloud.ai.rag2okf.common.request.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上传、解析和重分块共用的分块策略请求。
 *
 * <p>只允许传递非秘密策略参数；边界和层级由稳定枚举表达，未知值不得回退到默认策略。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChunkPolicyRequest {

    /** 内容边界策略。 */
    @NotNull
    private ChunkBoundaryType boundaryType;

    /** 块层级组织策略。 */
    @NotNull
    private ChunkHierarchyType hierarchyType;

    /** 受白名单约束的非秘密策略参数。 */
    private Map<String, Object> parameters = new LinkedHashMap<>();
}
