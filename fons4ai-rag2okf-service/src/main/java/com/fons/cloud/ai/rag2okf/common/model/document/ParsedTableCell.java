package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 规范化表格中的一个单元格。 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParsedTableCell {

    /** 行序号，从 0 开始。 */
    private int row;
    /** 列序号，从 0 开始。 */
    private int column;
    /** 单元格文本。 */
    private String text;
    /** 跨行数，默认 1。 */
    private int rowSpan;
    /** 跨列数，默认 1。 */
    private int colSpan;
}
