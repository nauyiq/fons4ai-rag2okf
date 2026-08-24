package com.fons.cloud.ai.rag2okf.common.constants.document;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档用户可见状态白名单。
 *
 * <p>用户视角只有 4 个业务状态（解析与分块合并为"解析"），{@link #DELETED} 为删除受理后的
 * 终态。状态流转白名单下沉到本枚举，供 {@code KbDocument} 领域命令和数据库条件更新共同校验，
 * 防止应用层散落状态机判断：</p>
 *
 * <ul>
 *   <li>{@code UPLOADED} → {@code PARSED}/{@code FAILED}/{@code DELETED}</li>
 *   <li>{@code PARSED} → {@code PUBLISHED}/{@code FAILED}/{@code DELETED}</li>
 *   <li>{@code PUBLISHED} → {@code PARSED}/{@code DELETED}</li>
 *   <li>{@code FAILED} → {@code UPLOADED}/{@code PARSED}/{@code DELETED}</li>
 *   <li>{@code DELETED} 为终态，不允许再流转</li>
 * </ul>
 *
 * <p>同状态重复流转不视为合法转移；RECHUNK 成功后停留在 {@code PARSED} 属于不流转，
 * 由调用方跳过状态更新表达。</p>
 *
 * @author hongqy
 */
@Getter
@AllArgsConstructor
public enum DocumentStatus {

    /** 上传成功，源文件已保存，尚未解析。 */
    UPLOADED("UPLOADED"),
    /** 解析与分块成功，可发布。 */
    PARSED("PARSED"),
    /** 发布成功，检索可见。 */
    PUBLISHED("PUBLISHED"),
    /** 最近一次处理失败，可重新发起。 */
    FAILED("FAILED"),
    /** 删除受理终态，业务记录保留但内容进入清理流程。 */
    DELETED("DELETED");

    /** 与数据库列保持兼容的持久化代码。 */
    @EnumValue
    private final String value;

    /**
     * 判断是否允许从当前状态流转到目标状态。
     *
     * @param target 目标状态
     * @return 在设计 §4.5 白名单内时返回 {@code true}
     */
    public boolean canTransitionTo(DocumentStatus target) {
        return switch (this) {
            case UPLOADED -> target == PARSED || target == FAILED || target == DELETED;
            case PARSED -> target == PUBLISHED || target == FAILED || target == DELETED;
            case PUBLISHED -> target == PARSED || target == DELETED;
            case FAILED -> target == UPLOADED || target == PARSED || target == DELETED;
            case DELETED -> false;
        };
    }
}
