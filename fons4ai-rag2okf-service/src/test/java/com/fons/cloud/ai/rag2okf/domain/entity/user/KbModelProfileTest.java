package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelTestStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link KbModelProfile} 的领域行为测试。
 */
class KbModelProfileTest {

    @Test
    void shouldCreateActiveProfile() {
        KbModelProfile profile = KbModelProfile.create(
                "profile-key", 10L, 20L, ModelType.EMBEDDING, "text-embedding",
                1024, null, "{\"timeoutSeconds\":30}");

        assertEquals(ModelProfileStatus.ACTIVE, profile.getStatus());
        assertEquals(10L, profile.getOwnerUserId());
        assertEquals(20L, profile.getConnectionId());
        assertEquals(ModelType.EMBEDDING, profile.getModelType());
        assertNull(profile.getContextWindowLength());
    }

    @Test
    void shouldUpdateMergedConfigurationAndRecordTestResult() {
        KbModelProfile profile = KbModelProfile.create(
                "profile-key", 10L, 20L, ModelType.EMBEDDING, "old-model",
                1024, 4096, "{}");
        Date testedAt = new Date();

        profile.updateConfiguration(null, 1536, 8192, "{\"timeoutSeconds\":60}",
                ModelProfileStatus.DISABLED);
        profile.recordTestResult(ModelTestStatus.SUCCEEDED, testedAt, null);

        assertEquals("old-model", profile.getModelName());
        assertEquals(1536, profile.getDimensions());
        assertEquals(8192, profile.getContextWindowLength());
        assertEquals("{\"timeoutSeconds\":60}", profile.getParametersJson());
        assertEquals(ModelProfileStatus.DISABLED, profile.getStatus());
        assertEquals(ModelTestStatus.SUCCEEDED, profile.getLastTestStatus());
        assertEquals(testedAt, profile.getLastTestAt());
        assertNull(profile.getLastTestErrorCode());
    }
}
