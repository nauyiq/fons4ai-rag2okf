package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 解析过程的安全化追踪信息。 */
@Getter @Setter @Accessors(fluent = true) @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ParseTrace {
    /** 按实际执行顺序记录的步骤。 */ private List<ParseTraceStep> steps = new ArrayList<>();
    /** @return 不含步骤的追踪信息。 */
    public static ParseTrace empty() { return new ParseTrace(new ArrayList<>()); }
}
