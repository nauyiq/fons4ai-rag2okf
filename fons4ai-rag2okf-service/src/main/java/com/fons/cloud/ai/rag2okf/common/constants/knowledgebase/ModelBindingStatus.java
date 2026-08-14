package com.fons.cloud.ai.rag2okf.common.constants.knowledgebase;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author hongqy
 */
@Getter
@AllArgsConstructor
public enum ModelBindingStatus {

    ACTIVE("ACTIVE"),
    DISABLED("DISABLED");

    @EnumValue
    private final String value;

}
