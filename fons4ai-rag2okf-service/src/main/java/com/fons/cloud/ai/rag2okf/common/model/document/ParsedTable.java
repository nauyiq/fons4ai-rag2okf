package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 规范化表格内容。 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParsedTable {

    /** 表格行数。 */
    private int rows;
    /** 表格列数。 */
    private int columns;
    /** 按行优先顺序保存的单元格。 */
    private List<ParsedTableCell> cells = new ArrayList<>();
}
