package com.fons.cloud.ai.rag2okf.common.exception.user;

import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.result.ResultCode;

/**
 * 当前本地用户不具备指定工作空间访问权限。
 *
 * @author hongqy
 */
public class WorkspaceAccessDeniedException extends BusinessRuntimeException {

    /**
     * 创建不泄露成员关系细节的授权失败异常。
     */
    public WorkspaceAccessDeniedException() {
        super(ResultCode.NOT_PERMISSION);
    }
}
