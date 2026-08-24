package com.fons.cloud.ai.rag2okf.common.constants.document;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档内容清理状态白名单。
 *
 * <p>描述删除受理后 MinIO 对象与 ES 投影等外部内容的物理清理进度，由删除服务与
 * DELETE_CLEANUP 任务驱动：</p>
 *
 * <ul>
 *   <li>{@code NOT_REQUIRED}：初始默认，未进入删除清理流程</li>
 *   <li>{@code PENDING}：软删除受理时置入，等待或正在清理</li>
 *   <li>{@code SUCCEEDED}：全部内容清理成功</li>
 *   <li>{@code FAILED}：清理失败，可通过清理重试入口再次发起</li>
 * </ul>
 *
 * <p>清理状态只对管理查询可见，不进入普通用户文档视图。</p>
 *
 * @author hongqy
 */
@Getter
@AllArgsConstructor
public enum CleanupStatus {

    /** 未进入删除清理流程。 */
    NOT_REQUIRED("NOT_REQUIRED"),
    /** 删除受理后等待或正在清理。 */
    PENDING("PENDING"),
    /** 全部外部内容清理成功。 */
    SUCCEEDED("SUCCEEDED"),
    /** 清理失败，可重试。 */
    FAILED("FAILED");

    /** 与数据库列保持兼容的持久化代码。 */
    @EnumValue
    private final String value;
}
