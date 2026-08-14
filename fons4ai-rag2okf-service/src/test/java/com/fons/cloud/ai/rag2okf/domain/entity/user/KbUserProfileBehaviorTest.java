package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.fons.cloud.ai.rag2okf.common.exception.user.InvalidUserProfileException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link KbUser} 用户资料行为测试。
 */
class KbUserProfileBehaviorTest {

    @Test
    void shouldApplyPatchAndShallowMergePreferenceObject() {
        KbUser user = user("原名称", "old-avatar", "{\"theme\":\"dark\",\"language\":\"zh-CN\"}");

        user.applyProfilePatch("  新名称  ", "  https://example.com/avatar.png  ",
                "{\"theme\":\"light\",\"defaultModels\":{\"llm\":\"profile-key\"}}");

        assertEquals("新名称", user.getDisplayName());
        assertEquals("https://example.com/avatar.png", user.getAvatarUrl());
        assertEquals("{\"theme\":\"light\",\"language\":\"zh-CN\","
                + "\"defaultModels\":{\"llm\":\"profile-key\"}}", user.getPreferenceJson());
    }

    @Test
    void shouldKeepUnsubmittedFieldsAndAllowClearingOptionalAvatar() {
        KbUser user = user("原名称", "old-avatar", "{\"theme\":\"dark\"}");

        user.applyProfilePatch(null, "   ", null);

        assertEquals("原名称", user.getDisplayName());
        assertNull(user.getAvatarUrl());
        assertEquals("{\"theme\":\"dark\"}", user.getPreferenceJson());
    }

    @Test
    void shouldTreatEmptyPreferenceObjectAsNoChange() {
        KbUser user = user("原名称", null, "{\"theme\":\"dark\"}");

        user.applyProfilePatch(null, null, "{}");

        assertEquals("{\"theme\":\"dark\"}", user.getPreferenceJson());
    }

    @Test
    void shouldKeepExistingPreferenceWhenPatchIsInvalid() {
        KbUser malformed = user("原名称", null, "{\"theme\":\"dark\"}");
        KbUser array = user("原名称", null, "{\"theme\":\"dark\"}");
        KbUser tooDeep = user("原名称", null, "{\"theme\":\"dark\"}");
        String tooDeepPatch = "{\"level\":".repeat(65) + "1" + "}".repeat(65);

        malformed.applyProfilePatch(null, null, "not-json");
        array.applyProfilePatch(null, null, "[]");
        tooDeep.applyProfilePatch(null, null, tooDeepPatch);

        assertEquals("{\"theme\":\"dark\"}", malformed.getPreferenceJson());
        assertEquals("{\"theme\":\"dark\"}", array.getPreferenceJson());
        assertEquals("{\"theme\":\"dark\"}", tooDeep.getPreferenceJson());
    }

    @Test
    void shouldRejectInvalidDisplayNameAndOversizedAvatar() {
        KbUser user = user("原名称", null, "{}");

        assertThrows(InvalidUserProfileException.class,
                () -> user.applyProfilePatch("   ", null, null));
        assertThrows(InvalidUserProfileException.class,
                () -> user.applyProfilePatch(null, "a".repeat(513), null));
    }

    private KbUser user(String displayName, String avatarUrl, String preferenceJson) {
        KbUser user = new KbUser();
        user.setDisplayName(displayName);
        user.setAvatarUrl(avatarUrl);
        user.setPreferenceJson(preferenceJson);
        return user;
    }
}
