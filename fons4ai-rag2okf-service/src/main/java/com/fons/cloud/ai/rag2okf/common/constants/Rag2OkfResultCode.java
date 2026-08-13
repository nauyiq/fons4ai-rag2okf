package com.fons.cloud.ai.rag2okf.common.constants;

import com.fons.cloud.common.result.Result;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author hongqy
 */
@Getter
@AllArgsConstructor
public enum Rag2OkfResultCode implements Result {

    //  ==================== 参数异常 ====================
    PASSWORD_INCORRECT("RF100001", "用户名或密码错误"),
    DUPLICATE_PASSWORD_INCORRECT("RF100002", "两次密码不一致"),
    USER_EXIST("RF100003", "用户已存在"),

    ;

    private final String code;
    private final String message;
}
