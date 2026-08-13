package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.db.entity.CommonEntity;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.util.Date;

/**
 * Rag2OKF 本地邮箱账号持久化实体。
 *
 * <p>邮箱字段只表示规范化登录标识；该实体不承载邮箱所有权已验证语义。
 * 密码摘要只允许认证应用服务写入，不得映射到通用响应对象。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true, exclude = "passwordHash")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_user")
public class KbUser extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 不可变用户业务标识，同时作为 Sa-Token loginId。 */
    private String userKey;

    /** trim 并整体转小写后的登录邮箱。 */
    private String email;

    /** 带算法标识和随机盐的密码摘要，禁止记录日志或返回客户端。 */
    private String passwordHash;

    /** 用户展示名称。 */
    private String displayName;

    /** 用户头像地址。 */
    private String avatarUrl;

    /** 账号状态：ACTIVE 启用，DISABLED 禁用。 */
    private UserStatus status;

    /** 用户界面与处理偏好的 JSON 快照。 */
    private String preferenceJson;

    /** 最近一次成功登录时间。 */
    private Date lastLoginAt;

    /** 当前密码摘要生效时间。 */
    private Date passwordChangedAt;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }


    public static KbUser create(String userKey, String passwordHash, String email, String displayName) {
        KbUser user = new KbUser();
        user.setUserKey(userKey);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(StringUtils.isBlank(displayName) ? email : displayName);
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferenceJson("{}");
        user.setPasswordChangedAt(new Date());
        return user;
    }

}
