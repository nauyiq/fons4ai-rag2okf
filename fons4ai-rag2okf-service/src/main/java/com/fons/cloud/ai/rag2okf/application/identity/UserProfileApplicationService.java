package com.fons.cloud.ai.rag2okf.application.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceMemberEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMapper;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbWorkspaceMemberMapper;
import com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException;
import com.fons.cloud.ai.rag2okf.common.exeception.InvalidUserProfileException;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.LocalAccountRepository;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceMemberStatus;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 当前已登录用户资料的应用服务。
 *
 * @author hongqy
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileApplicationService {

    private final LocalAccountRepository accountRepository;
    private final SaTokenAuthTemplate saTokenAuthTemplate;
    private final KbWorkspaceMapper workspaceMapper;
    private final KbWorkspaceMemberMapper workspaceMemberMapper;

    /** preferenceJson 合并用 JSON 解析器；限制最大嵌套深度以防御恶意超深嵌套导致的栈溢出。 */
    private static final ObjectMapper PREFERENCE_MAPPER;

    /** preferenceJson 允许的最大 JSON 嵌套深度，超过则按非法 JSON 容错回退。 */
    private static final int PREFERENCE_MAX_NESTING_DEPTH = 64;

    static {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(PREFERENCE_MAX_NESTING_DEPTH).build());
        PREFERENCE_MAPPER = mapper;
    }

    /**
     * 获取当前会话所属用户的安全资料快照。
     *
     * @return 当前活跃本地账号
     */
    public KbUserEntity currentUser() {
        if (!saTokenAuthTemplate.isLogin()) {
            throw new AuthenticationDeniedException();
        }
        String userKey = saTokenAuthTemplate.getCurrentLoginIdAsString();
        KbUserEntity user = accountRepository.findByUserKey(userKey).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            saTokenAuthTemplate.kickout(userKey);
            throw new AuthenticationDeniedException();
        }
        return user;
    }

    /**
     * 获取当前用户的个人工作空间。
     *
     * @param user 当前用户
     * @return 个人工作空间实体，不存在时返回 null
     */
    public KbWorkspaceEntity currentWorkspace(KbUserEntity user) {
        return workspaceMapper.selectOne(
                new LambdaQueryWrapper<KbWorkspaceEntity>()
                        .eq(KbWorkspaceEntity::getOwnerUserId, user.getId()));
    }

    /**
     * 获取当前用户在工作空间中的成员关系。
     *
     * @param user 当前用户
     * @param workspace 工作空间
     * @return 成员关系实体，不存在时返回 null
     */
    public KbWorkspaceMemberEntity currentMembership(KbUserEntity user, KbWorkspaceEntity workspace) {
        if (workspace == null) {
            return null;
        }
        return workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<KbWorkspaceMemberEntity>()
                        .eq(KbWorkspaceMemberEntity::getWorkspaceId, workspace.getId())
                        .eq(KbWorkspaceMemberEntity::getUserId, user.getId())
                        .eq(KbWorkspaceMemberEntity::getStatus, WorkspaceMemberStatus.ACTIVE));
    }

    /**
     * 更新当前用户允许自行编辑的资料白名单。
     *
     * @param displayName 展示名称
     * @param avatarUrl 头像地址
     * @param preferenceJson 用户偏好快照
     * @return 更新后的当前用户
     */
    public KbUserEntity updateCurrentUser(String displayName, String avatarUrl, String preferenceJson) {
        KbUserEntity user = currentUser();
        user.setDisplayName(normalizeDisplayName(displayName));
        user.setAvatarUrl(normalizeOptional(avatarUrl, 512));
        // preferenceJson 采用局部合并：只替换提交的顶层 key，保留其它已有偏好；
        // 空提交（null/空白/{}) 不清除现有偏好，保证幂等安全。
        String merged = mergePreferenceJson(user.getPreferenceJson(), preferenceJson);
        user.setPreferenceJson(normalizeOptional(merged, 8_192));
        accountRepository.updateProfile(user);
        return user;
    }

    private String normalizeDisplayName(String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.isBlank() || normalized.length() > 64) {
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

    /**
     * 局部合并 preferenceJson：只替换提交的顶层子节点，保留其它已有偏好。
     *
     * <p>例如现有 {"theme":"dark","language":"zh-CN"}，提交 {"defaultModels":{...}}，
     * 合并后为 {"theme":"dark","language":"zh-CN","defaultModels":{...}}。
     * patch 中存在的顶层 key 整体替换 existing 中同名 key（浅合并，不做深层递归合并）；
     * patch 中不存在的 key 保留 existing 原值。</p>
     *
     * <p>合并规则与容错：</p>
     * <ul>
     *   <li>patch 为 null 或空白 → 返回 existing（不清除现有偏好，保证幂等安全）</li>
     *   <li>existing 为 null 或空白 → 返回 patch（首次设置）</li>
     *   <li>两者都非空 → 解析为 JSON object 后按顶层 key 合并</li>
     *   <li>patch 为空对象 {} → 视为空提交，返回 existing 不变</li>
     *   <li>非法 JSON、超深嵌套或任一端非 JSON object → 回退返回 existing，不抛错</li>
     * </ul>
     *
     * @param existingJson 当前已持久化的偏好 JSON，可为 null
     * @param patchJson 本次提交的偏好 JSON 补丁，可为 null
     * @return 合并后的偏好 JSON 字符串
     */
    static String mergePreferenceJson(String existingJson, String patchJson) {
        // 空提交（null/空白）不清除现有偏好
        if (patchJson == null || patchJson.isBlank()) {
            return existingJson;
        }
        String patch = patchJson.trim();
        // 首次设置：无现有偏好时直接采用 patch
        if (existingJson == null || existingJson.isBlank()) {
            return patch;
        }
        // 两者均非空：解析为 JSON object 后按顶层 key 局部合并
        try {
            JsonNode existingNode = PREFERENCE_MAPPER.readTree(existingJson);
            JsonNode patchNode = PREFERENCE_MAPPER.readTree(patch);
            // 任一端非 object 时保守保留 existing，避免破坏既有数据
            if (!existingNode.isObject() || !patchNode.isObject()) {
                return existingJson;
            }
            ObjectNode existingObj = (ObjectNode) existingNode;
            ObjectNode patchObj = (ObjectNode) patchNode;
            // 空对象 patch 视为空提交，不清除现有偏好
            if (patchObj.isEmpty()) {
                return existingJson;
            }
            // 浅合并：patch 中存在的顶层 key 整体替换 existing 中同名 key
            List<String> changedKeys = new ArrayList<>();
            patchObj.fields().forEachRemaining(entry -> {
                existingObj.set(entry.getKey(), entry.getValue());
                changedKeys.add(entry.getKey());
            });
            // 仅记录变更的顶层 key 列表，不记录值（defaultModels 可能含 profileKey 等引用，保持不记敏感原值习惯）
            log.info("preferenceJson 局部合并，变更顶层 key: {}", changedKeys);
            return PREFERENCE_MAPPER.writeValueAsString(existingObj);
        } catch (Exception e) {
            // 非法 JSON 或超深嵌套：回退到 existing，不抛错
            return existingJson;
        }
    }
}
