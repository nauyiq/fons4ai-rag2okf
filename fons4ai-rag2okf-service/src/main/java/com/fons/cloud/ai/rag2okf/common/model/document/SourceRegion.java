package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 页面中的归一化区域坐标。 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SourceRegion {
    /** 左上角横坐标，范围为 [0, 1]。 */ private double x;
    /** 左上角纵坐标，范围为 [0, 1]。 */ private double y;
    /** 区域宽度，范围为 (0, 1]。 */ private double width;
    /** 区域高度，范围为 (0, 1]。 */ private double height;
}
