package com.fons.cloud.ai.rag2okf.common.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 最近一次模型能力测试的安全化状态。
 *
 * @author hongqy
 */
public enum ModelTestStatus {
    /** 最近一次测试成功。 */
    SUCCEEDED("SUCCEEDED"),
    /** 最近一次测试失败，错误细节不对外暴露。 */
    FAILED("FAILED");

    @EnumValue
    private final String value;

    ModelTestStatus(String value) {
        this.value = value;
    }

    /** @return 数据库存储代码 */
    public String getValue() {
        return value;
    }
}
