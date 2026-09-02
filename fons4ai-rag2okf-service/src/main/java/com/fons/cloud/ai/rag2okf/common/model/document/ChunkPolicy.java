package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/** 解析任务冻结的分块策略。 */
@Getter @Setter @Accessors(fluent = true) @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ChunkPolicy {
    /** 内容边界策略。 */ private ChunkBoundaryType boundaryType;
    /** 块组织策略。 */ private ChunkHierarchyType hierarchyType;
    /** 策略非秘密参数。 */ private Map<String, Object> parameters = new LinkedHashMap<>();
}
