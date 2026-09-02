package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 本次解析实际使用的解析器信息。
 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParsedParserInfo {
    /**
     * 解析器类型。
     */
    private String type;
    /**
     * 解析工作流名称，不携带版本号或厂商内部协议。
     */
    private String workflow;
}
