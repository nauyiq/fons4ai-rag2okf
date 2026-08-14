package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.fons.cloud.db.entity.CommonEntity;
import com.fons.cloud.ai.rag2okf.common.constants.user.UserStatus;
import com.fons.cloud.ai.rag2okf.common.exception.user.InvalidUserProfileException;
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

    private static final int DISPLAY_NAME_MAX_LENGTH = 64;
    private static final int AVATAR_URL_MAX_LENGTH = 512;
    private static final int PREFERENCE_JSON_MAX_LENGTH = 8_192;
    private static final int PREFERENCE_MAX_NESTING_DEPTH = 64;

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

    /**
     * 将允许用户自行维护的资料补丁应用到当前实体。
     *
     * <p>未提交字段保持原值；空头像表示清除。偏好 JSON 采用顶层浅合并，
     * 空对象、非法 JSON 或非对象补丁保持原值。</p>
     *
     * @param displayName 展示名称补丁，可为空
     * @param avatarUrl 头像地址补丁，可为空
     * @param preferenceJson 偏好 JSON 补丁，可为空
     */
    public void applyProfilePatch(String displayName, String avatarUrl, String preferenceJson) {
        if (displayName != null) {
            this.displayName = normalizeDisplayName(displayName);
        }
        if (avatarUrl != null) {
            this.avatarUrl = normalizeOptional(avatarUrl, AVATAR_URL_MAX_LENGTH);
        }
        String mergedPreference = mergePreferenceJson(this.preferenceJson, preferenceJson);
        this.preferenceJson = normalizeOptional(mergedPreference, PREFERENCE_JSON_MAX_LENGTH);
    }

    private String normalizeDisplayName(String value) {
        String normalized = value.trim();
        if (normalized.isBlank() || normalized.length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new InvalidUserProfileException();
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new InvalidUserProfileException();
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static String mergePreferenceJson(String existingJson, String patchJson) {
        if (patchJson == null || patchJson.isBlank()) {
            return existingJson;
        }
        try {
            Object patchValue = JSON.parse(patchJson.trim(), createReaderContext());
            if (!(patchValue instanceof JSONObject patchObject) || patchObject.isEmpty()) {
                return existingJson;
            }
            if (existingJson == null || existingJson.isBlank()) {
                return patchObject.toJSONString();
            }
            Object existingValue = JSON.parse(existingJson, createReaderContext());
            if (!(existingValue instanceof JSONObject existingObject)) {
                return existingJson;
            }
            patchObject.forEach(existingObject::put);
            return existingObject.toJSONString();
        } catch (JSONException exception) {
            return existingJson;
        }
    }

    private static JSONReader.Context createReaderContext() {
        JSONReader.Context context = new JSONReader.Context();
        context.setMaxLevel(PREFERENCE_MAX_NESTING_DEPTH);
        return context;
    }

}
